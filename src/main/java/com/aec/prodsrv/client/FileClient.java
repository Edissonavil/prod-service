package com.aec.prodsrv.client;

import com.aec.prodsrv.client.dto.FileInfoDto;
import com.aec.prodsrv.security.JwtAuthenticationFilter.CustomWebAuthenticationDetails;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Component
public class FileClient {

    private static final Logger log = LoggerFactory.getLogger(FileClient.class);

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public FileClient(@Value("${file-service.base-url}") String baseUrl,
            WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    private String getAuthToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            throw new IllegalStateException("No hay usuario autenticado");
        if (auth.getDetails() instanceof CustomWebAuthenticationDetails c)
            return c.getJwtToken();
        if (auth.getCredentials() instanceof String s && !s.isBlank())
            return s;
        if (auth instanceof JwtAuthenticationToken jwtAuth)
            return jwtAuth.getToken().getTokenValue();
        throw new IllegalStateException("No se encontró JWT");
    }

    public FileInfoDto uploadProductFile(MultipartFile file, String uploader, Long productId) {
        return upload(file, uploader, productId, true);
    }

    public FileInfoDto uploadReceiptFile(MultipartFile file, String uploader, Long orderId) {
        return upload(file, uploader, orderId, false);
    }

    private FileInfoDto upload(MultipartFile file, String uploader, Long entityId, boolean isProduct) {
        if (file == null) {
            log.warn("upload() llamado con file = null (entityId={}, isProduct={})", entityId, isProduct);
            return null;
        }
        if (file.isEmpty()) {
            log.warn("upload() MultipartFile vacío: originalFilename={}, size=0 (entityId={}, isProduct={})",
                    file.getOriginalFilename(), entityId, isProduct);
            return null;
        }

        log.info(
                "Preparando subida a file-service. originalFilename={}, contentType={}, size={} bytes, entityId={}, isProduct={}",
                file.getOriginalFilename(), file.getContentType(), file.getSize(), entityId, isProduct);

        ByteArrayResource resource;
        try {
            byte[] bytes = file.getBytes();
            resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            log.info("Bytes listos para enviar: {} bytes", bytes.length);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo bytes del fichero: " + e.getMessage(), e);
        }

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", resource);
        form.add("uploader", uploader);

        String token = getAuthToken();
        String uri = String.format("/api/files/public/%d?type=%s", entityId, isProduct ? "product" : "receipt");

        return webClient.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(BodyInserters.fromMultipartData(form))
                .exchangeToMono(resp -> {
                    HttpStatusCode status = resp.statusCode();
                    return resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                log.info("FileService {} -> status={}, body={}", uri, status.value(), body);
                                if (!status.is2xxSuccessful()) {
                                    throw new IllegalStateException("FileService " + uri + " -> " + status.value());
                                }
                                try {
                                    FileInfoDto dto = mapper.readValue(body, FileInfoDto.class);
                                    log.info("DTO deserializado: driveFileId={}, filename={}",
                                            dto.getDriveFileId(), dto.getFilename());
                                    return dto;
                                } catch (Exception ex) {
                                    log.error("Error deserializando JSON: {}", ex.getMessage(), ex);
                                    throw new IllegalStateException("No se pudo parsear respuesta de file-service", ex);
                                }
                            });
                })
                .block();
    }

    public void deleteFile(String driveFileId) {
        String token = getAuthToken();
        webClient.delete()
                .uri("/api/files/{driveId}", driveFileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public void deleteProductFolder(Long productId) {
        String token = getAuthToken();
        webClient.delete()
                .uri("/api/files/product/{productId}/folder", productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public byte[] downloadFile(String driveFileId) {
        return webClient.get()
                .uri("/api/files/{driveId}", driveFileId)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    public List<FileInfoDto> getProductFilesMeta(Long productId) {
        String token = getAuthToken();
        return webClient.get()
                .uri("/api/files/meta/product/{id}", productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToFlux(FileInfoDto.class)
                .collectList()
                .block();
    }

    private String getAuthTokenOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            return null;
        if (auth.getDetails() instanceof CustomWebAuthenticationDetails c)
            return c.getJwtToken();
        if (auth.getCredentials() instanceof String s && !s.isBlank())
            return s;
        if (auth instanceof JwtAuthenticationToken jwtAuth)
            return jwtAuth.getToken().getTokenValue();
        return null;
    }

    public List<FileInfoDto> getMetaByProduct(Long productId) {
        String token = getAuthTokenOrNull(); // ← NO lanza excepción

        WebClient.RequestHeadersSpec<?> req = webClient.get()
                .uri("/api/files/meta/product/{productId}", productId);

        if (token != null) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return req.retrieve()
                .bodyToFlux(FileInfoDto.class)
                .collectList()
                .onErrorResume(ex -> {
                    log.warn("getMetaByProduct({}) falló: {}", productId, ex.getMessage());
                    return Mono.just(List.of());
                })
                .block();
    }

    // en FileClient: añade helpers para STAGING

    public StagingInfoDto uploadToStaging(MultipartFile file, Long productId) {
        if (file == null || file.isEmpty())
            return null;

        String token = getAuthToken();
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/files/staging/{pid}").build(productId))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(BodyInserters.fromMultipartData(buildStreamingForm(file)))
                .retrieve()
                .bodyToMono(StagingInfoDto.class)
                .block();
    }

    public List<StagingInfoDto> listStaging(Long productId) {
        String token = getAuthTokenOrNull();
        WebClient.RequestHeadersSpec<?> req = webClient.get().uri("/api/files/staging/{pid}", productId);
        if (token != null)
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return req.retrieve().bodyToFlux(StagingInfoDto.class).collectList().block();
    }

    public List<FileInfoDto> promoteStaging(Long productId) {
        String token = getAuthToken();
        return webClient.post()
                .uri("/api/files/staging/{pid}/promote", productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToFlux(FileInfoDto.class)
                .collectList()
                .block();
    }

    public void discardStaging(Long productId) {
        String token = getAuthToken();
        webClient.delete()
                .uri("/api/files/staging/{pid}", productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private MultiValueMap<String, Object> buildStreamingForm(MultipartFile file) {
        var map = new LinkedMultiValueMap<String, Object>();
        // NO leer bytes a memoria: usa InputStreamResource
        InputStreamResource resource = new InputStreamResource(io(file)) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }

            @Override
            public long contentLength() {
                try {
                    return file.getSize();
                } catch (Exception e) {
                    return -1;
                }
            }
        };
        map.add("file", resource);
        return map;
    }

    private InputStream io(MultipartFile f) {
        try {
            return f.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // DTO de staging (mismo shape que en file-service)
    @Data
    public static class StagingInfoDto {
        private String stagingId;
        private String filename;
        private String contentType;
        private long size;
        private String previewUri;
    }

}
