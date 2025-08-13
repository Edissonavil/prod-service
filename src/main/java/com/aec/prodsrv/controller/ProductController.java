package com.aec.prodsrv.controller;

import com.aec.prodsrv.dto.AdminDecisionDto;
import com.aec.prodsrv.dto.ProductDto;
import com.aec.prodsrv.model.Product;
import com.aec.prodsrv.model.ProductStatus;
import com.aec.prodsrv.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Collections;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
        private static final Logger log = LoggerFactory.getLogger(ProductController.class); 

    private final ProductService svc;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public ResponseEntity<ProductDto> create(
            @RequestPart("dto") @Valid ProductDto dto,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "fotos", required = false) List<MultipartFile> fotos, 
            @RequestPart(value = "archivosAut", required = false) List<MultipartFile> archivosAut,
            @AuthenticationPrincipal Jwt jwt) {
        String uploader = jwt.getSubject();
        ProductDto created = svc.create(dto, foto,fotos, archivosAut, uploader);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public ResponseEntity<ProductDto> update(
            @PathVariable Long id,
            @RequestPart("dto") @Valid ProductDto dto,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "fotos", required = false) List<MultipartFile> fotos, 
            @RequestPart(value = "archivosAut", required = false) List<MultipartFile> archivosAut,
            @RequestPart(value = "keepFotoIds", required = false) String keepFotoIdsJson,
            @RequestPart(value = "autKeepUrls", required = false) String autKeepUrlsJson,
            @AuthenticationPrincipal Jwt jwt) {
        String uploader = jwt.getSubject();
        List<String> keepFotoIds = readListOrEmpty(keepFotoIdsJson);
        List<String> autKeepUrls = readListOrEmpty(autKeepUrlsJson);
        ProductDto updated = svc.update(id, dto, foto, fotos, archivosAut, keepFotoIds, autKeepUrls, uploader);
        return ResponseEntity.ok(updated);
    }

    private List<String> readListOrEmpty(String json) {
        try {
            if (json == null || json.isBlank()) return Collections.emptyList();
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("No se pudo parsear lista JSON recibida en multipart: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        svc.deleteProduct(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/my-products") // La ruta real que el servicio recibe después de StripPrefix
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public Page<ProductDto> myProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {

        log.info(">>>>>> ProdService: Recibida solicitud GET /my-products. Subject JWT: {}", jwt.getSubject()); // ¡Añade esta línea!

        Pageable pg = PageRequest.of(page, size);
        Page<ProductDto> result = svc.findByUploaderId(jwt.getSubject(), pg);

        if (result.isEmpty()) {
            log.warn(">>>>>> ProdService: findByUploaderId devolvió una página vacía para el subject {}", jwt.getSubject());
        } else {
            log.info(">>>>>> ProdService: findByUploaderId encontró {} elementos.", result.getTotalElements());
        }

        return result; // Asegúrate de que estás devolviendo el resultado aquí
    }


    @GetMapping
    public Page<ProductDto> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) ProductStatus estado) {
        Pageable pg = PageRequest.of(page, size);
        return (estado != null) ? svc.findByEstado(estado, pg) : svc.findAll(pg);
    }

    @GetMapping("/{id}")
    public ProductDto byId(@PathVariable Long id) {
        return svc.getById(id); // si no existe lanza EntityNotFoundException
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ROL_ADMIN')")
    public Page<ProductDto> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return svc.pendientes(PageRequest.of(page, size));
    }

    @PutMapping(path = "/{id}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROL_ADMIN')")
    public ProductDto decide(
            @PathVariable Long id,
            @RequestBody @Valid AdminDecisionDto decisionDto,
            @AuthenticationPrincipal Jwt jwt) {
        return svc.decidir(id, decisionDto.getAprobar(), decisionDto.getComentario(), jwt.getSubject());
    }

    @GetMapping("/uploader/{username}")
    @PreAuthorize("hasAuthority('ROL_COLABORADOR') or hasAuthority('ROL_ADMIN')")
    public List<ProductDto> findByUploader(
            @PathVariable("username") String username,
            @RequestHeader("Authorization") String bearer) {
        return svc.findByUploaderUsername(username);
    }
}
