package com.aec.prodsrv.service;

import com.aec.prodsrv.client.FileClient;
import com.aec.prodsrv.client.UsersClient;
import com.aec.prodsrv.client.dto.FileInfoDto;
import com.aec.prodsrv.dto.ProductDto;
import com.aec.prodsrv.model.Category;
import com.aec.prodsrv.model.Product;
import com.aec.prodsrv.model.ProductStatus;
import com.aec.prodsrv.repository.CategoryRepository;
import com.aec.prodsrv.repository.ProductRepository;
import com.aec.prodsrv.service.EmailService;
import com.aec.prodsrv.client.UsersClient;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repo;
    private final CategoryRepository catRepo;
    private final FileClient fileClient;
    private final EmailService emailService;
    private final UsersClient usersClient;

    @Value("${file-service.base-url}")
    private String fileServiceBaseUrl; // uso interno (S2S) si lo necesitas

    @Value("${gateway.public-base-url}")
    private String gatewayBaseUrl; // para construir URLs consumibles por el navegador

    public ProductService(ProductRepository repo,
            CategoryRepository catRepo,
            FileClient fileClient,
            EmailService emailService,
            UsersClient usersClient) {
        this.repo = repo;
        this.catRepo = catRepo;
        this.fileClient = fileClient;
        this.emailService = emailService;
        this.usersClient = usersClient;
    }

    @PostConstruct
    void logBases() {
        log.info("file-service.base-url     = {}", fileServiceBaseUrl);
        log.info("gateway.public-base-url  = {}", gatewayBaseUrl);
    }

    public org.springframework.data.domain.Page<ProductDto> pendientes(org.springframework.data.domain.Pageable pg) {
        return repo.findByEstado(ProductStatus.PENDIENTE, pg).map(this::toDto);
    }

    public ProductDto decidir(Long id, boolean aprobar, String comentario, String adminUsername) {
        Product p = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));

        p.setEstado(aprobar ? ProductStatus.APROBADO : ProductStatus.RECHAZADO);
        p.setUsuarioDecision(adminUsername);
        p.setComentario(comentario);

        // Aplicamos decisión primero en memoria
        if (aprobar) {
            // PROMOVER staging -> Drive + BDD (solo ahora se escriben StoredFile y se
            // obtienen driveFileId)
            List<FileInfoDto> permanentes = List.of();
            try {
                permanentes = fileClient.promoteStaging(p.getIdProducto());
            } catch (Exception e) {
                log.error("[DECIDIR] Promoción de staging falló: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo promover archivos a permanente");
            }

            // Separa imágenes de no-imágenes
            List<String> fotos = permanentes.stream()
                    .filter(f -> f.getFileType() != null && f.getFileType().startsWith("image/"))
                    .map(FileInfoDto::getDriveFileId)
                    .toList();

            List<String> aut = permanentes.stream()
                    .filter(f -> f.getFileType() == null || !f.getFileType().startsWith("image/"))
                    .map(FileInfoDto::getDriveFileId)
                    .toList();

            p.setFotografiaProd(fotos.isEmpty() ? null : new ArrayList<>(fotos));
            p.setArchivosAut(aut.isEmpty() ? null : new ArrayList<>(aut));
        } else {
            try {
                fileClient.discardStaging(p.getIdProducto());
            } catch (Exception e) {
                log.warn("[DECIDIR] Fallo descartando staging de producto {}: {}", id, e.toString());
            }
            // Asegura no dejar restos
            p.setFotografiaProd(null);
            p.setArchivosAut(null);
        }

        Product saved = repo.save(p);

        try {
            String uploaderUsername = saved.getUploaderUsername();
            var emailOpt = usersClient.findEmailByUsername(uploaderUsername);
            String to = emailOpt.orElse(null);
            log.info("[DECIDIR] uploader='{}' -> email='{}'", uploaderUsername, to);

            String portadaUrl = null;
            if (saved.getFotografiaProd() != null && !saved.getFotografiaProd().isEmpty()) {
                String first = saved.getFotografiaProd().get(0);
                portadaUrl = gatewayBaseUrl + "/api/files/" + saved.getIdProducto() + "/" + first;
            }
            log.info("[DECIDIR] portadaUrl={}", portadaUrl);

            if (to == null || to.isBlank()) {
                log.warn("[DECIDIR] No hay email del colaborador. No se enviará notificación.");
            } else if (aprobar) {
                emailService.sendProductApprovedEmail(
                        to, uploaderUsername, saved.getIdProducto(), saved.getNombre(), portadaUrl, comentario);
            } else {
                emailService.sendProductRejectedEmail(
                        to, uploaderUsername, saved.getIdProducto(), saved.getNombre(), comentario);
            }
        } catch (Exception e) {
            log.warn("[DECIDIR] Fallo al enviar notificación de decisión del producto {}: {}", id, e.toString());
        }

        return toDto(saved);
    }

    public ProductDto create(@Valid ProductDto dto,
            MultipartFile foto,
            List<MultipartFile> fotos,
            List<MultipartFile> archivosAut,
            String uploader) {

        log.info("=== INICIANDO CREACIÓN DE PRODUCTO ===");
        log.info("Datos recibidos - Nombre: {}, Uploader: {}", dto.getNombre(), uploader);
        log.info("Foto única recibida: {}", (foto != null ? foto.getOriginalFilename() : "null"));
        log.info("Fotos múltiples recibidas: {}", (fotos != null ? fotos.size() : 0));
        log.info("Archivos recibidos: {}", (archivosAut != null ? archivosAut.size() : 0));

        // 1) Resolver categorías/especialidades
        Set<Category> cats = namesToCategorySet(dto.getCategorias());
        Set<Category> specs = namesToCategorySet(dto.getEspecialidades());

        // 2) Crear entidad base PENDIENTE
        Product p = Product.builder()
                .nombre(dto.getNombre())
                .descripcionProd(dto.getDescripcionProd())
                .precioIndividual(dto.getPrecioIndividual())
                .pais(dto.getPais())
                .estado(ProductStatus.PENDIENTE)
                .uploaderUsername(uploader)
                .categorias(cats)
                .especialidades(specs)
                .build();

        // 3) Primer guardado para obtener ID
        Product saved = repo.save(p);
        repo.flush();
        final Long productId = saved.getIdProducto(); // <- capturamos ID en variable final
        log.info("Producto guardado inicialmente con ID: {}", productId);

        boolean hasChanges = false;

        // 4) Subir FOTOS (múltiples y compatibilidad con única)
        List<String> fotoIds = new ArrayList<>();

        if (fotos != null && !fotos.isEmpty()) {
            for (MultipartFile f : fotos) {
                if (f == null || f.isEmpty())
                    continue;
                try {
                    FileInfoDto res = fileClient.uploadProductFile(f, uploader, productId);
                    if (res != null && res.getDriveFileId() != null && !res.getDriveFileId().trim().isEmpty()) {
                        fotoIds.add(res.getDriveFileId());
                        log.info("Foto subida (múltiple): {} → {}", f.getOriginalFilename(), res.getDriveFileId());
                    } else {
                        log.warn("Respuesta nula/sin driveFileId para foto múltiple: {}", f.getOriginalFilename());
                    }
                } catch (Exception e) {
                    log.error("Error subiendo foto múltiple {}: {}", f.getOriginalFilename(), e.getMessage(), e);
                }
            }
        }

        if (fotoIds.isEmpty() && foto != null && !foto.isEmpty()) {
            try {
                FileInfoDto res = fileClient.uploadProductFile(foto, uploader, productId);
                if (res != null && res.getDriveFileId() != null && !res.getDriveFileId().trim().isEmpty()) {
                    fotoIds.add(res.getDriveFileId());
                    log.info("Foto subida (única): {} → {}", foto.getOriginalFilename(), res.getDriveFileId());
                } else {
                    log.warn("Respuesta nula/sin driveFileId para foto única");
                }
            } catch (Exception e) {
                log.error("Error subiendo foto única: {}", e.getMessage(), e);
            }
        }

        if (!fotoIds.isEmpty()) {
            saved.setFotografiaProd(fotoIds);
            hasChanges = true;
        }

        // 5) Subir ARCHIVOS AUTORIZADOS
        if (archivosAut != null && !archivosAut.isEmpty()) {
            List<String> driveIds = new ArrayList<>();
            for (MultipartFile mf : archivosAut) {
                if (mf == null || mf.isEmpty())
                    continue;
                try {
                    FileInfoDto res = fileClient.uploadProductFile(mf, uploader, productId);
                    if (res != null && res.getDriveFileId() != null && !res.getDriveFileId().trim().isEmpty()) {
                        driveIds.add(res.getDriveFileId());
                        log.info("Archivo agregado: {} → {}", mf.getOriginalFilename(), res.getDriveFileId());
                    } else {
                        log.warn("Respuesta nula/sin driveFileId para archivo: {}", mf.getOriginalFilename());
                    }
                } catch (Exception e) {
                    log.error("Error subiendo archivo {}: {}", mf.getOriginalFilename(), e.getMessage(), e);
                }
            }
            if (!driveIds.isEmpty()) {
                saved.setArchivosAut(driveIds);
                hasChanges = true;
            }
        }

        // 6) Segundo guardado (si hubo cambios)
        if (hasChanges) {
            saved = repo.save(saved);
            repo.flush();
            log.info("Producto {} actualizado con multimedia. Fotos: {}, Archivos: {}",
                    productId,
                    (saved.getFotografiaProd() == null ? 0 : saved.getFotografiaProd().size()),
                    (saved.getArchivosAut() == null ? 0 : saved.getArchivosAut().size()));
        } else {
            log.info("No hubo cambios de archivos para guardar en producto {}", productId);
        }

        // 7) Email al ADMIN (usamos productId final y NO capturamos 'saved' en lambdas)
        try {
            List<String> portadaIds = (saved.getFotografiaProd() == null) ? List.of() : saved.getFotografiaProd();
            List<String> portadaUrls = portadaIds.stream()
                    .map(fid -> gatewayBaseUrl + "/api/files/" + productId + "/" + fid) // usa productId (final)
                    .toList();

            List<String> catsText = (dto.getCategorias() != null) ? dto.getCategorias()
                    : saved.getCategorias().stream().map(Category::getNombre).toList();
            List<String> specsText = (dto.getEspecialidades() != null) ? dto.getEspecialidades()
                    : saved.getEspecialidades().stream().map(Category::getNombre).toList();

            emailService.sendNewProductForReviewEmail(
                    uploader,
                    productId,
                    saved.getNombre(),
                    catsText,
                    specsText,
                    portadaUrls);
            log.info("Notificación al admin enviada para el producto {}", productId);
        } catch (Exception ex) {
            log.warn("No se pudo enviar el email para el producto {}: {}", productId, ex.getMessage());
        }

        log.info("=== CREACIÓN DE PRODUCTO COMPLETADA (ID: {}) ===", productId);
        return toDto(saved);
    }

    public ProductDto update(Long id,
            @Valid ProductDto dto,
            MultipartFile foto,
            List<MultipartFile> fotos,
            List<MultipartFile> archivosAut,
            List<String> keepFotoIds,
            List<String> autKeepUrls,
            String uploader) {

        Product p = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));

        if (!Objects.equals(p.getUploaderUsername(), uploader)) {
            throw new SecurityException("Sin permiso");
        }

        boolean esPendiente = p.getEstado() == ProductStatus.PENDIENTE;
        if (esPendiente) {
            if (fotos != null)
                for (var f : fotos)
                    fileClient.uploadToStaging(f, id);
            if (foto != null && !foto.isEmpty())
                fileClient.uploadToStaging(foto, id);
            if (archivosAut != null)
                for (var mf : archivosAut)
                    fileClient.uploadToStaging(mf, id);
        }

        boolean esAprobado = p.getEstado() == ProductStatus.APROBADO;

        List<String> existingFotoIds = (p.getFotografiaProd() != null)
                ? new ArrayList<>(p.getFotografiaProd())
                : new ArrayList<>();

        List<String> keepIds = (keepFotoIds != null && !keepFotoIds.isEmpty())
                ? new ArrayList<>(keepFotoIds)
                : new ArrayList<>(existingFotoIds); // si no envían keep, conservamos

        for (String oldId : existingFotoIds) {
            if (!keepIds.contains(oldId)) {
                try {
                    fileClient.deleteFile(oldId);
                } catch (Exception e) {
                    log.warn("No se pudo eliminar foto {} en Drive: {}", oldId, e.getMessage());
                }
            }
        }

        // Subir nuevas fotos (múltiples)
        List<String> uploadedFotoIds = new ArrayList<>();
        if (fotos != null) {
            for (MultipartFile f : fotos) {
                if (f == null || f.isEmpty())
                    continue;
                FileInfoDto res = fileClient.uploadProductFile(f, uploader, id);
                if (res != null && res.getDriveFileId() != null) {
                    uploadedFotoIds.add(res.getDriveFileId());
                }
            }
        }
        // Compatibilidad: una sola 'foto'
        if (foto != null && !foto.isEmpty()) {
            FileInfoDto res = fileClient.uploadProductFile(foto, uploader, id);
            if (res != null && res.getDriveFileId() != null) {
                uploadedFotoIds.add(res.getDriveFileId());
            }
        }

        // Resultado final de fotos = keep + nuevos
        List<String> finalFotoIds = new ArrayList<>(keepIds);
        finalFotoIds.addAll(uploadedFotoIds);
        p.setFotografiaProd(finalFotoIds.isEmpty() ? null : finalFotoIds);

        // ------------------------------------
        // ARCHIVOS AUTORIZADOS -> **NO PERMITIDO** si está APROBADO
        // ------------------------------------
        if (!esAprobado) {
            List<String> existingAutIds = (p.getArchivosAut() != null)
                    ? new ArrayList<>(p.getArchivosAut())
                    : new ArrayList<>();

            List<String> keepAutIds = new ArrayList<>();
            if (autKeepUrls != null && !autKeepUrls.isEmpty()) {
                for (String url : autKeepUrls) {
                    String idFromUrl = extractDriveId(url);
                    if (idFromUrl != null && !idFromUrl.isBlank())
                        keepAutIds.add(idFromUrl);
                }
            } else {
                keepAutIds.addAll(existingAutIds);
            }

            for (String oldId : existingAutIds) {
                if (!keepAutIds.contains(oldId)) {
                    try {
                        fileClient.deleteFile(oldId);
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar archivo AUT {} en Drive: {}", oldId, e.getMessage());
                    }
                }
            }

            List<String> uploadedAutIds = new ArrayList<>();
            if (archivosAut != null) {
                for (MultipartFile mf : archivosAut) {
                    if (mf == null || mf.isEmpty())
                        continue;
                    FileInfoDto res = fileClient.uploadProductFile(mf, uploader, id);
                    if (res != null && res.getDriveFileId() != null) {
                        uploadedAutIds.add(res.getDriveFileId());
                    } else {
                        log.warn("Subida de archivo AUT sin driveFileId. original={}", mf.getOriginalFilename());
                    }
                }
            }
            List<String> finalAutIds = new ArrayList<>(keepAutIds);
            finalAutIds.addAll(uploadedAutIds);
            p.setArchivosAut(finalAutIds.isEmpty() ? null : finalAutIds);
        } else {
            // Si está APROBADO, ignoramos cambios sobre archivos autorizados (se mantienen
            // tal cual)
            log.info("[UPDATE] Producto {} está APROBADO: cambios en archivos AUT ignorados", id);
        }

        // ------------------------------------
        // CAMPOS EDITABLES
        // ------------------------------------
        p.setNombre(dto.getNombre());
        p.setDescripcionProd(dto.getDescripcionProd());
        p.setPrecioIndividual(dto.getPrecioIndividual());

        if (!esAprobado) {
            p.setPais(dto.getPais());
            if (dto.getCategorias() != null) {
                p.setCategorias(namesToCategorySet(dto.getCategorias()));
            }
            if (dto.getEspecialidades() != null) {
                p.setEspecialidades(namesToCategorySet(dto.getEspecialidades()));
            }
        } else {
            log.info("[UPDATE] Producto {} está APROBADO: se ignoran cambios en país/categorías/especialidades", id);
        }

        return toDto(repo.save(p));
    }

    private String extractDriveId(String urlOrId) {
        if (urlOrId == null)
            return null;
        int idx = urlOrId.lastIndexOf('/');
        return (idx >= 0) ? urlOrId.substring(idx + 1) : urlOrId;
    }

    public void deleteProduct(Long id, String uploader) {
        Product p = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));
        if (!Objects.equals(p.getUploaderUsername(), uploader)) {
            throw new SecurityException("No autorizado");
        }

        // 1) Borrar CARPETA del producto en Drive (recursivo) vía file-service
        boolean folderOk = deleteFolderWithRetry(p.getIdProducto(), 3, 150);
        if (!folderOk) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo eliminar la carpeta del producto en Drive (tras reintentos)");
        }

        // 2) Si TODO salió bien en Drive, recién borramos en BD
        repo.delete(p);
    }

    /** Reintenta la eliminación de la carpeta del producto en Drive. */
    private boolean deleteFolderWithRetry(Long productId, int maxAttempts, long backoffMillis) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                fileClient.deleteProductFolder(productId);
                return true;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    log.warn("Fallo al eliminar carpeta de producto {} tras {} intentos: {}",
                            productId, maxAttempts, e.getMessage());
                    return false;
                }
                try {
                    Thread.sleep(backoffMillis * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private boolean deleteWithRetry(String driveFileId, int maxAttempts, long backoffMillis) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                fileClient.deleteFile(driveFileId);
                return true;
            } catch (Exception e) {
                // último intento fallido → registrar y devolver false
                if (attempt == maxAttempts) {
                    log.warn("Fallo al eliminar en Drive {} tras {} intentos: {}", driveFileId, maxAttempts,
                            e.getMessage());
                    return false;
                }
                try {
                    Thread.sleep(backoffMillis * attempt); // backoff incremental
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public org.springframework.data.domain.Page<ProductDto> findAll(org.springframework.data.domain.Pageable pg) {
        return repo.findAll(pg).map(this::toDto);
    }

    public org.springframework.data.domain.Page<ProductDto> findByEstado(ProductStatus e,
            org.springframework.data.domain.Pageable pg) {
        return repo.findByEstado(e, pg).map(this::toDto);
    }

    public org.springframework.data.domain.Page<ProductDto> findByUploaderId(String u,
            org.springframework.data.domain.Pageable pg) {
        return repo.findByUploaderUsername(u, pg).map(this::toDto);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public ProductDto getById(Long id) {
        return toDto(
                repo.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Producto con ID " + id + " no existe")));
    }

    private Set<Category> namesToCategorySet(List<String> names) {
        if (names == null)
            return Collections.emptySet();
        return names.stream().map(this::resolveOrCreateCategory).collect(Collectors.toSet());
    }

    private Category resolveOrCreateCategory(String nombre) {
        return catRepo.findByNombreIgnoreCase(nombre)
                .orElseGet(() -> catRepo.save(Category.builder().nombre(nombre).build()));
    }

    public List<ProductDto> findByUploaderUsername(String username) {
        return repo.findByUploaderUsername(username).stream().map(this::toDto).toList();
    }

    private static String onlyExt(String originalName) {
        if (originalName == null)
            return null;
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1)
            return null;
        return originalName.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    private ProductDto toDto(Product p) {
        // 1) URLs de fotos desde fotografiaProd (si viene)
        List<String> fotoUrls = (p.getFotografiaProd() != null && !p.getFotografiaProd().isEmpty())
                ? p.getFotografiaProd().stream()
                        .map(id -> gatewayBaseUrl + "/api/files/" + p.getIdProducto() + "/" + id)
                        .toList()
                : List.of();

        // 2) URLs de archivos autorizados
        List<String> autUrls = (p.getArchivosAut() != null && !p.getArchivosAut().isEmpty())
                ? p.getArchivosAut().stream()
                        .map(id -> gatewayBaseUrl + "/api/files/" + p.getIdProducto() + "/" + id)
                        .toList()
                : List.of();

        // 3) Consultar metadatos una sola vez (sirven para formatos y para fallback de

        List<String> formatos = List.of();
        List<String> imageIdsFromMeta = List.of();
        try {
            var metas = fileClient.getMetaByProduct(p.getIdProducto());
            if (metas != null && !metas.isEmpty()) {
                formatos = metas.stream()
                        .filter(m -> m.getFileType() == null || !m.getFileType().startsWith("image/"))
                        .map(m -> onlyExt(m.getOriginalName()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

                imageIdsFromMeta = metas.stream()
                        .filter(m -> m.getFileType() != null && m.getFileType().startsWith("image/"))
                        .map(m -> m.getDriveFileId())
                        .filter(Objects::nonNull)
                        .toList();

                log.info("Formatos para producto {}: {}", p.getIdProducto(), formatos);
            } else {
                log.info("Metadatos vacíos para producto {}", p.getIdProducto());
            }
        } catch (Exception e) {
            log.warn("No se pudieron obtener metadatos para producto {}: {}", p.getIdProducto(), e.getMessage());
        }

        // 4) Fallback de fotos: si fotografiaProd está vacío, usar las imágenes
        List<String> fotografiaProdForDto = (p.getFotografiaProd() != null) ? p.getFotografiaProd() : List.of();
        if ((fotografiaProdForDto == null || fotografiaProdForDto.isEmpty()) && !imageIdsFromMeta.isEmpty()) {
            fotografiaProdForDto = imageIdsFromMeta;
            if (fotoUrls.isEmpty()) {
                fotoUrls = imageIdsFromMeta.stream()
                        .map(id -> gatewayBaseUrl + "/api/files/" + p.getIdProducto() + "/" + id)
                        .toList();
            }
        }

        return ProductDto.builder()
                .idProducto(p.getIdProducto())
                .nombre(p.getNombre())
                .descripcionProd(p.getDescripcionProd())
                .precioIndividual(p.getPrecioIndividual())
                .fotografiaProd(fotografiaProdForDto)
                .fotografiaUrl(fotoUrls)
                .archivosAut(p.getArchivosAut())
                .archivosAutUrls(autUrls)
                .formatos(formatos)
                .estado(p.getEstado().name())
                .categorias(p.getCategorias().stream().map(Category::getNombre).toList())
                .especialidades(p.getEspecialidades().stream().map(Category::getNombre).toList())
                .pais(p.getPais())
                .uploaderUsername(p.getUploaderUsername())
                .usuarioDecision(p.getUsuarioDecision())
                .comentario(p.getComentario())
                .build();
    }
}
