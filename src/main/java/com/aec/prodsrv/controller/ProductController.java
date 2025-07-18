package com.aec.prodsrv.controller;

import com.aec.prodsrv.dto.AdminDecisionDto;
import com.aec.prodsrv.dto.ProductDto;
import com.aec.prodsrv.model.ProductStatus;
import com.aec.prodsrv.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService svc;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public ResponseEntity<ProductDto> create(
            @RequestPart("dto") @Valid ProductDto dto,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "archivosAut", required = false) List<MultipartFile> archivosAut,
            @AuthenticationPrincipal Jwt jwt) {
        String uploader = jwt.getSubject();
        ProductDto created = svc.create(dto, foto, archivosAut, uploader);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public ResponseEntity<ProductDto> update(
            @PathVariable Long id,
            @RequestPart("dto") @Valid ProductDto dto,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "archivosAut", required = false) List<MultipartFile> archivosAut,
            @AuthenticationPrincipal Jwt jwt) {
        String uploader = jwt.getSubject();
        ProductDto updated = svc.update(id, dto, foto, archivosAut, uploader);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        svc.deleteProduct(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-products")
    @PreAuthorize("hasAuthority('ROL_COLABORADOR')")
    public Page<ProductDto> myProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        Pageable pg = PageRequest.of(page, size);
        return svc.findByUploaderId(jwt.getSubject(), pg);
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
