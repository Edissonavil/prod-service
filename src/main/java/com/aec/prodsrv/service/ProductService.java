package com.aec.prodsrv.service;

import com.aec.prodsrv.client.FileClient;
import com.aec.prodsrv.client.dto.FileInfoDto;
import com.aec.prodsrv.dto.ProductDto;
import com.aec.prodsrv.model.Category;
import com.aec.prodsrv.model.Product;
import com.aec.prodsrv.model.ProductStatus;
import com.aec.prodsrv.repository.CategoryRepository;
import com.aec.prodsrv.repository.ProductRepository;
import com.aec.prodsrv.service.EmailService;
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

    @Value("${file-service.base-url}")
    private String fileServiceBaseUrl; // uso interno (S2S) si lo necesitas

    @Value("${gateway.public-base-url}")
    private String gatewayBaseUrl; // para construir URLs consumibles por el navegador

    public ProductService(ProductRepository repo,
            CategoryRepository catRepo,
            FileClient fileClient,
            EmailService emailService) {
        this.repo = repo;
        this.catRepo = catRepo;
        this.fileClient = fileClient;
        this.emailService = emailService;
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
        return toDto(repo.save(p));
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
            String uploader) {

        Product p = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));

        if (!Objects.equals(p.getUploaderUsername(), uploader)) {
            throw new SecurityException("Sin permiso");
        }

        // Foto
        if (fotos != null) {
            // Borrar fotos anteriores
            if (p.getFotografiaProd() != null) {
                for (String oldId : p.getFotografiaProd()) {
                    try {
                        fileClient.deleteFile(oldId);
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar foto {}", oldId);
                    }
                }
            }
            // Subir nuevas
            List<String> nuevosIds = new ArrayList<>();
            for (MultipartFile f : fotos) {
                if (f == null || f.isEmpty())
                    continue;
                FileInfoDto res = fileClient.uploadProductFile(f, uploader, id);
                if (res != null && res.getDriveFileId() != null) {
                    nuevosIds.add(res.getDriveFileId());
                }
            }
            p.setFotografiaProd(nuevosIds);
        } else if (foto != null) {
            // Compatibilidad
            if (!foto.isEmpty()) {
                if (p.getFotografiaProd() != null) {
                    for (String oldId : p.getFotografiaProd()) {
                        try {
                            fileClient.deleteFile(oldId);
                        } catch (Exception e) {
                        }
                    }
                }
                FileInfoDto res = fileClient.uploadProductFile(foto, uploader, id);
                if (res != null && res.getDriveFileId() != null) {
                    p.setFotografiaProd(new ArrayList<>(List.of(res.getDriveFileId())));
                }
            } else {
                p.setFotografiaProd(null);
            }
        }

        // Archivos autorizados (si el cliente envía la parte, incluso vacía)
        if (archivosAut != null) {
            if (p.getArchivosAut() != null) {
                for (String oldId : p.getArchivosAut()) {
                    try {
                        fileClient.deleteFile(oldId);
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar el archivo antiguo {} para producto {}: {}", oldId, id,
                                e.getMessage());
                    }
                }
            }

            List<String> nuevos = new ArrayList<>();
            for (MultipartFile mf : archivosAut) {
                if (mf == null || mf.isEmpty())
                    continue;
                FileInfoDto res = fileClient.uploadProductFile(mf, uploader, id);
                if (res != null && res.getDriveFileId() != null) {
                    nuevos.add(res.getDriveFileId());
                } else {
                    log.warn("Subida de archivo en update devolvió respuesta nula o sin driveFileId. original={}",
                            mf.getOriginalFilename());
                }
            }
            p.setArchivosAut(nuevos);
        }

        // Resto de campos
        p.setNombre(dto.getNombre());
        p.setDescripcionProd(dto.getDescripcionProd());
        p.setPrecioIndividual(dto.getPrecioIndividual());
        p.setPais(dto.getPais());
        if (dto.getCategorias() != null) {
            p.setCategorias(namesToCategorySet(dto.getCategorias()));
        }
        if (dto.getEspecialidades() != null) {
            p.setEspecialidades(namesToCategorySet(dto.getEspecialidades()));
        }

        return toDto(repo.save(p));
    }

    public void deleteProduct(Long id, String uploader) {
        Product p = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));
        if (!Objects.equals(p.getUploaderUsername(), uploader)) {
            throw new SecurityException("No autorizado");
        }

        // 1) Intentar eliminar TODO en Drive primero (con reintentos)
        List<String> targets = new ArrayList<>();
        if (p.getFotografiaProd() != null)
            targets.addAll(p.getFotografiaProd());
        if (p.getArchivosAut() != null)
            targets.addAll(p.getArchivosAut());

        List<String> fallos = new ArrayList<>();
        for (String fid : targets) {
            if (fid == null || fid.isBlank())
                continue;
            boolean ok = deleteWithRetry(fid, 3, 150); // 3 intentos, 150 ms backoff base
            if (!ok) {
                fallos.add(fid);
            }
        }

        if (!fallos.isEmpty()) {
            // Abortamos para que NO se borre en BD: todo o nada
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudieron eliminar en Drive los siguientes archivos: " + String.join(", ", fallos));
        }

        // 2) Si TODO salió bien en Drive, recién borramos en BD
        repo.delete(p);
    }

    /**
     * Reintenta la eliminación en Drive con backoff lineal simple.
     * true = eliminado, false = falló tras agotar reintentos.
     */
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
        List<String> fotoUrls = (p.getFotografiaProd() != null)
                ? p.getFotografiaProd().stream()
                        .map(id -> gatewayBaseUrl + "/api/files/" + p.getIdProducto() + "/" + id)
                        .toList()
                : List.of();

        List<String> autUrls = (p.getArchivosAut() != null)
                ? p.getArchivosAut().stream()
                        .map(id -> gatewayBaseUrl + "/api/files/" + p.getIdProducto() + "/" + id)
                        .toList()
                : List.of();

        List<String> formatos = List.of();
        try {
            var metas = fileClient.getMetaByProduct(p.getIdProducto());
            if (metas != null && !metas.isEmpty()) {
                formatos = metas.stream()
                        // si NO quieres contar la foto: descarta mime image/*
                        .filter(m -> m.getFileType() == null || !m.getFileType().startsWith("image/"))
                        .map(m -> onlyExt(m.getOriginalName()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                log.info("Formatos para producto {}: {}", p.getIdProducto(), formatos);
            } else {
                log.info("Metadatos vacíos para producto {}", p.getIdProducto());
            }
        } catch (Exception e) {
            log.warn("No se pudieron obtener formatos para producto {}: {}", p.getIdProducto(), e.getMessage());
        }

        return ProductDto.builder()
                .idProducto(p.getIdProducto())
                .nombre(p.getNombre())
                .descripcionProd(p.getDescripcionProd())
                .precioIndividual(p.getPrecioIndividual())
                .fotografiaProd(p.getFotografiaProd())
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
