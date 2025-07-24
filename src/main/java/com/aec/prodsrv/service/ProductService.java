package com.aec.prodsrv.service;

import com.aec.prodsrv.client.FileClient;
import com.aec.prodsrv.client.FileClient.UploadFileResponse;
import com.aec.prodsrv.dto.ProductDto;
import com.aec.prodsrv.model.*;
import com.aec.prodsrv.repository.*;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository repo;
    private final CategoryRepository catRepo;
    private final FileClient fileClient;
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Value("${file-service.base-url}")
    private String fileServiceBaseUrl;

    public Page<ProductDto> pendientes(Pageable pg) {
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

    public ProductDto create(ProductDto dto,
            MultipartFile foto,
            List<MultipartFile> archivosAut,
            String uploader) {

        Set<Category> cats = namesToCategorySet(dto.getCategorias());
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

        // foto
        if (foto != null && !foto.isEmpty()) {
            try {
                // ✅ Obtener UploadFileResponse
                UploadFileResponse res = fileClient.uploadProductFile(foto, uploader, productId);
                // ✅ Guardar el googleDriveFileId en fotografiaProd
                saved.setFotografiaProd(res.googleDriveFileId());
            } catch (Exception e) {
                log.error("Error subiendo foto para producto {}: {}", productId, e.getMessage());
                // Considera relanzar o manejar más robustamente
            }
        }

        // archivos autorizados
        if (archivosAut != null) {
            List<String> googleDriveFileIds = new ArrayList<>(); // ✅ Cambiado a almacenar IDs
            for (MultipartFile mf : archivosAut) {
                if (!mf.isEmpty()) {
                    try {
                        // ✅ Obtener UploadFileResponse
                        UploadFileResponse res = fileClient.uploadProductFile(mf, uploader, productId);
                        // ✅ Guardar el googleDriveFileId
                        googleDriveFileIds.add(res.googleDriveFileId());
                    } catch (Exception e) {
                        log.error("Error subiendo archivo {}: {}", mf.getOriginalFilename(), e.getMessage());
                        // Considera relanzar o manejar más robustamente
                    }
                }
            }
            // ✅ Asignar la lista de Google Drive IDs
            saved.setArchivosAut(googleDriveFileIds);
        }

        // Guarda el producto nuevamente con los IDs de archivos actualizados
        return toDto(repo.save(saved));
    }

    public ProductDto update(Long id,
            ProductDto dto,
            MultipartFile foto,
            List<MultipartFile> archivosAut,
            String uploader) {

        Product p = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));
        if (!p.getUploaderUsername().equals(uploader)) {
            throw new SecurityException("Sin permiso");
        }

        if (foto != null) {
            if (!foto.isEmpty()) {
                // ✅ Borrar la foto anterior si existe antes de subir la nueva
                if (p.getFotografiaProd() != null) {
                    try {
                        fileClient.deleteFile(p.getFotografiaProd()); // ✅ Borrar la foto antigua de GD
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar la foto anterior {} para producto {}: {}", p.getFotografiaProd(),
                                id, e.getMessage());
                    }
                }
                UploadFileResponse res = fileClient.uploadProductFile(foto, uploader, id);
                p.setFotografiaProd(res.googleDriveFileId());
            } else { // Si el MultipartFile se envió pero estaba vacío, asumimos que se quiere borrar
                if (p.getFotografiaProd() != null) {
                    try {
                        fileClient.deleteFile(p.getFotografiaProd()); // ✅ Borrar la foto antigua de GD
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar la foto vacía para producto {}: {}", id, e.getMessage());
                    }
                }
                p.setFotografiaProd(null); // Eliminar referencia en DB
            }
        }

        // archivos autorizados
        if (archivosAut != null) { // Si el cliente envía `archivosAut` (incluso vacío)
            // Primero, eliminar los archivos antiguos de Google Drive
            if (p.getArchivosAut() != null) {
                for (String oldFileId : p.getArchivosAut()) {
                    try {
                        fileClient.deleteFile(oldFileId); // ✅ Borrar archivos antiguos de GD
                    } catch (Exception e) {
                        log.warn("No se pudo eliminar el archivo antiguo {} para producto {}: {}", oldFileId, id,
                                e.getMessage());
                    }
                }
            }

            List<String> newGoogleDriveFileIds = new ArrayList<>(); // ✅ Almacenar nuevos IDs
            for (MultipartFile mf : archivosAut) {
                if (!mf.isEmpty()) {
                    UploadFileResponse res = fileClient.uploadProductFile(mf, uploader, id);
                    newGoogleDriveFileIds.add(res.googleDriveFileId());
                }
            }
            p.setArchivosAut(newGoogleDriveFileIds); // ✅ Asignar la nueva lista de IDs
        }

        // resto de campos
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
        if (!p.getUploaderUsername().equals(uploader))
            throw new SecurityException("No autorizado");
        repo.delete(p);
    }

    public Page<ProductDto> findAll(Pageable pg) {
        return repo.findAll(pg).map(this::toDto);
    }

    public Page<ProductDto> findByEstado(ProductStatus e, Pageable pg) {
        return repo.findByEstado(e, pg).map(this::toDto);
    }

    public Page<ProductDto> findByUploaderId(String u, Pageable pg) {
        return repo.findByUploaderUsername(u, pg).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        return toDto(
                repo.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Producto con ID " + id + " no existe")));
    }

    private Set<Category> namesToCategorySet(List<String> names) {
        if (names == null)
            return Collections.emptySet();
        return names.stream()
                .map(this::resolveOrCreateCategory)
                .collect(Collectors.toSet());
    }

    private Category resolveOrCreateCategory(String nombre) {
        return catRepo.findByNombreIgnoreCase(nombre)

                .orElseGet(() -> catRepo.save(Category.builder().nombre(nombre).build()));
    }

    public List<ProductDto> findByUploaderUsername(String uploader) {
        return repo.findByUploaderUsername(uploader)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ProductDto toDto(Product p) {
        String fotoUrl = (p.getFotografiaProd() != null)
                ? fileServiceBaseUrl + "/api/files/download/" + p.getFotografiaProd() // ✅ URL de descarga de Google
                                                                                      // Drive
                : null;

        List<String> autUrls = (p.getArchivosAut() != null)
                ? p.getArchivosAut().stream()
                        .map(googleDriveFileId -> fileServiceBaseUrl + "/api/files/download/" + googleDriveFileId) // ✅
                                                                                                                   // URL
                                                                                                                   // de
                                                                                                                   // descarga
                                                                                                                   // de
                                                                                                                   // Google
                                                                                                                   // Drive
                        .toList()
                : Collections.emptyList();

        return ProductDto.builder()
                .idProducto(p.getIdProducto())
                .nombre(p.getNombre())
                .descripcionProd(p.getDescripcionProd())
                .precioIndividual(p.getPrecioIndividual())
                .fotografiaProd(p.getFotografiaProd()) // Sigue siendo el ID de Google Drive
                .fotografiaUrl(fotoUrl)
                .archivosAut(p.getArchivosAut()) // Sigue siendo la lista de IDs de Google Drive
                .archivosAutUrls(autUrls)
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
