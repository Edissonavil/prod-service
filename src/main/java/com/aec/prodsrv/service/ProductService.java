package com.aec.prodsrv.service;

import com.aec.prodsrv.client.FileClient;
import com.aec.prodsrv.client.dto.FileInfoDto;
import com.aec.prodsrv.dto.ProductDto;
import com.aec.prodsrv.model.Category;
import com.aec.prodsrv.model.Product;
import com.aec.prodsrv.model.ProductStatus;
import com.aec.prodsrv.repository.CategoryRepository;
import com.aec.prodsrv.repository.ProductRepository;
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

    @Value("${file-service.base-url}")
    private String fileServiceBaseUrl;   // uso interno (S2S) si lo necesitas

    @Value("${gateway.public-base-url}")
    private String gatewayBaseUrl;       // para construir URLs consumibles por el navegador

    public ProductService(ProductRepository repo,
                          CategoryRepository catRepo,
                          FileClient fileClient) {
        this.repo = repo;
        this.catRepo = catRepo;
        this.fileClient = fileClient;
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
                             List<MultipartFile> archivosAut,
                             String uploader) {

        Set<Category> cats  = namesToCategorySet(dto.getCategorias());
        Set<Category> specs = namesToCategorySet(dto.getEspecialidades());

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

        Product saved = repo.save(p);
        Long productId = saved.getIdProducto();
        log.info("Producto guardado con ID: {}", productId);

        // Foto
        if (foto != null && !foto.isEmpty()) {
            try {
                FileInfoDto res = fileClient.uploadProductFile(foto, uploader, productId);
                log.info("Respuesta subida foto: {}", res != null ? res.getDriveFileId() : "null");
                if (res != null && res.getDriveFileId() != null) {
                    saved.setFotografiaProd(res.getDriveFileId());
                } else {
                    log.warn("Upload foto devolvió respuesta nula o sin driveFileId para producto {}", productId);
                }
            } catch (Exception e) {
                log.error("Error subiendo foto para producto {}: {}", productId, e.getMessage(), e);
            }
        }

        // Archivos autorizados
        if (archivosAut != null) {
            List<String> driveIds = new ArrayList<>();
            for (MultipartFile mf : archivosAut) {
                if (mf != null && !mf.isEmpty()) {
                    try {
                        FileInfoDto res = fileClient.uploadProductFile(mf, uploader, productId);
                        log.info("Respuesta subida archivo {}: {}", mf.getOriginalFilename(),
                                res != null ? res.getDriveFileId() : "null");
                        if (res != null && res.getDriveFileId() != null) {
                            driveIds.add(res.getDriveFileId());
                        } else {
                            log.warn("Upload archivo {} devolvió respuesta nula/sin driveFileId",
                                    mf.getOriginalFilename());
                        }
                    } catch (Exception e) {
                        log.error("Error subiendo archivo {}: {}", mf.getOriginalFilename(), e.getMessage(), e);
                    }
                }
            }
            saved.setArchivosAut(driveIds);
        }

        // Persistir cambios de ficheros
        saved = repo.save(saved);
        return toDto(saved);
    }

    public ProductDto update(Long id,
                             @Valid ProductDto dto,
                             MultipartFile foto,
                             List<MultipartFile> archivosAut,
                             String uploader) {

        Product p = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));

        if (!Objects.equals(p.getUploaderUsername(), uploader)) {
            throw new SecurityException("Sin permiso");
        }

        // Foto
        if (foto != null) {
            if (!foto.isEmpty()) {
                if (p.getFotografiaProd() != null) {
                    try {
                        fileClient.deleteFile(p.getFotografiaProd()); // borra anterior en Drive
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar la foto anterior {} para producto {}: {}",
                                p.getFotografiaProd(), id, e.getMessage());
                    }
                }
                FileInfoDto res = fileClient.uploadProductFile(foto, uploader, id);
                if (res != null && res.getDriveFileId() != null) {
                    p.setFotografiaProd(res.getDriveFileId());
                } else {
                    log.warn("Subida de foto en update devolvió respuesta nula o sin driveFileId");
                }
            } else {
                // Se envió parte 'foto' pero vacía: eliminar
                if (p.getFotografiaProd() != null) {
                    try {
                        fileClient.deleteFile(p.getFotografiaProd());
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar la foto vacía para producto {}: {}", id, e.getMessage());
                    }
                }
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
                if (mf == null || mf.isEmpty()) continue;
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
        // Opcional: borrar archivos de Drive
        if (p.getFotografiaProd() != null) {
            try { fileClient.deleteFile(p.getFotografiaProd()); } catch (Exception ignored) {}
        }
        if (p.getArchivosAut() != null) {
            for (String fid : p.getArchivosAut()) {
                try { fileClient.deleteFile(fid); } catch (Exception ignored) {}
            }
        }
        repo.delete(p);
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
                                HttpStatus.NOT_FOUND, "Producto con ID " + id + " no existe"
                        ))
        );
    }

    private Set<Category> namesToCategorySet(List<String> names) {
        if (names == null) return Collections.emptySet();
        return names.stream().map(this::resolveOrCreateCategory).collect(Collectors.toSet());
    }

    private Category resolveOrCreateCategory(String nombre) {
        return catRepo.findByNombreIgnoreCase(nombre)
                .orElseGet(() -> catRepo.save(Category.builder().nombre(nombre).build()));
    }

    public List<ProductDto> findByUploaderUsername(String username) {
        return repo.findByUploaderUsername(username).stream().map(this::toDto).toList();
    }

    private ProductDto toDto(Product p) {
        String fotoUrl = (p.getFotografiaProd() != null)
                ? gatewayBaseUrl + "/api/files/" + p.getFotografiaProd()
                : null;

        List<String> autUrls = (p.getArchivosAut() != null)
                ? p.getArchivosAut().stream()
                    .map(id -> gatewayBaseUrl + "/api/files/" + id)
                    .toList()
                : List.of();

        return ProductDto.builder()
                .idProducto(p.getIdProducto())
                .nombre(p.getNombre())
                .descripcionProd(p.getDescripcionProd())
                .precioIndividual(p.getPrecioIndividual())
                .fotografiaUrl(fotoUrl)                  // URL vía Gateway
                .archivosAutUrls(autUrls)                // URLs vía Gateway
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
