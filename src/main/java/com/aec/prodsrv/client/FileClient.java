package com.aec.prodsrv.client;

import com.aec.prodsrv.client.dto.FileInfoDto;
import com.aec.prodsrv.security.JwtAuthenticationFilter.CustomWebAuthenticationDetails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Component
public class FileClient {

    private static final Logger log = LoggerFactory.getLogger(FileClient.class);

    private final WebClient webClient;

    public FileClient(@Value("${file-service.base-url}") String baseUrl,
                      WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    private String getAuthToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new IllegalStateException("No hay usuario autenticado en el SecurityContext");

        if (auth.getDetails() instanceof CustomWebAuthenticationDetails c) {
            return c.getJwtToken();
        }
        if (auth.getCredentials() instanceof String creds && !creds.isBlank()) {
            return creds;
        }
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        throw new IllegalStateException("No se encontró JWT en el SecurityContext");
    }

    public FileInfoDto uploadProductFile(MultipartFile file, String uploader, Long productId) {
        return upload(file, uploader, productId, true);
    }

    public FileInfoDto uploadReceiptFile(MultipartFile file, String uploader, Long orderId) {
        return upload(file, uploader, orderId, false);
    }

    private FileInfoDto upload(MultipartFile file, String uploader, Long entityId, boolean isProduct) {
        if (file == null || file.isEmpty()) return null;

        ByteArrayResource resource;
        try {
            resource = new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            };
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
                .exchangeToMono(resp -> handleResponse(resp, uri))
                .block();
    }

    private Mono<FileInfoDto> handleResponse(ClientResponse resp, String uri) {
        HttpStatusCode status = resp.statusCode();
        if (status.is2xxSuccessful()) {
            // Log del JSON crudo para confirmar el payload
            return resp.bodyToMono(String.class)
                    .doOnNext(json -> log.info("FileService {} OK. JSON crudo: {}", uri, json))
                    .flatMap(json -> resp.bodyToMono(FileInfoDto.class));
        }
        // Si no es 2xx, loguear cuerpo de error y fallar explícitamente
        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    log.error("FileService {} ERROR status={}, body={}", uri, status.value(), body);
                    return Mono.error(new IllegalStateException(
                            "FileService respondió " + status.value() + " en " + uri));
                });
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

    public byte[] downloadFile(String driveFileId) {
        return webClient.get()
                .uri("/api/files/{driveId}", driveFileId)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }
}
