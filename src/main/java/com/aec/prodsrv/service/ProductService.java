package com.aec.prodsrv.service;

import com.aec.prodsrv.client.FileClient;
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

        // foto
        if (foto != null && !foto.isEmpty()) {
            try {
                Map<String,Object> res = fileClient.uploadProductFile(foto, uploader, productId);
                saved.setFotografiaProd((String)res.get("filename"));
            } catch (Exception e) {
                log.error("Error subiendo foto para producto {}: {}", productId, e.getMessage());
            }
        }

        // archivos autorizados
        if (archivosAut != null) {
            List<String> filenames = new ArrayList<>();
            for (MultipartFile mf : archivosAut) {
                if (!mf.isEmpty()) {
                    try {
                        Map<String,Object> res = fileClient.uploadProductFile(mf, uploader, productId);
                        filenames.add((String)res.get("filename"));
                    } catch (Exception e) {
                        log.error("Error subiendo archivo {}: {}", mf.getOriginalFilename(), e.getMessage());
                    }
                }
            }
            saved.setArchivosAut(filenames);
        }

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

        // foto
        if (foto != null) {
            if (!foto.isEmpty()) {
                Map<String,Object> res = fileClient.uploadProductFile(foto, uploader, id);
                p.setFotografiaProd((String)res.get("filename"));
            } else {
                p.setFotografiaProd(null);
            }
        }

        // archivos autorizados
        if (archivosAut != null) {
            List<String> filenames = new ArrayList<>();
            for (MultipartFile mf : archivosAut) {
                if (!mf.isEmpty()) {
                    Map<String,Object> res = fileClient.uploadProductFile(mf, uploader, id);
                    filenames.add((String)res.get("filename"));
                }
            }
            p.setArchivosAut(filenames);
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

    public Page<ProductDto> findAll(Pageable pg)       { return repo.findAll(pg).map(this::toDto); }
    public Page<ProductDto> findByEstado(ProductStatus e, Pageable pg){ return repo.findByEstado(e, pg).map(this::toDto); }
    public Page<ProductDto> findByUploaderId(String u, Pageable pg){ return repo.findByUploaderUsername(u, pg).map(this::toDto); }
@Transactional(readOnly = true)
public ProductDto getById(Long id) {
    return toDto(
      repo.findById(id)
          .orElseThrow(() ->
            new ResponseStatusException(
              HttpStatus.NOT_FOUND,
              "Producto con ID " + id + " no existe"
            )
          )
    );
}
    private Set<Category> namesToCategorySet(List<String> names){
        if (names == null) return Collections.emptySet();
        return names.stream()
                    .map(this::resolveOrCreateCategory)
                    .collect(Collectors.toSet());
    }

    private Category resolveOrCreateCategory(String nombre){
        return catRepo.findByNombreIgnoreCase(nombre)
                      .orElseGet(() -> catRepo.save(Category.builder().nombre(nombre).build()));
    }

    private ProductDto toDto(Product p){
        String fotoUrl = (p.getFotografiaProd() != null)
            ? fileServiceBaseUrl + "/api/files/" + p.getIdProducto() + "/" + p.getFotografiaProd()
            : null;
        List<String> autUrls = (p.getArchivosAut()!=null)
            ? p.getArchivosAut().stream()
                .map(fn -> fileServiceBaseUrl + "/api/files/" + p.getIdProducto() + "/" + fn)
                .toList()
            : List.of();

        return ProductDto.builder()
                .idProducto(p.getIdProducto())
                .nombre(p.getNombre())
                .descripcionProd(p.getDescripcionProd())
                .precioIndividual(p.getPrecioIndividual())
                .fotografiaProd(p.getFotografiaProd())
                .fotografiaUrl(fotoUrl)
                .archivosAut(p.getArchivosAut())
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

    public List<ProductDto> findByUploaderUsername(String uploader) {
    return repo.findByUploaderUsername(uploader)
               .stream()
               .map(this::toDto)
               .toList();
}

}
