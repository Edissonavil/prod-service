package com.aec.prodsrv.client;

import com.aec.prodsrv.dto.FileInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.MultipartBodyBuilder;

import java.io.IOException;

@Component
public class FileClient {

    private final WebClient webClient;

    public FileClient(@Value("${file-service.base-url}") String baseUrl,
                      WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    private String getAuthToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        throw new IllegalStateException("No JWT en SecurityContext");
    }

    private MultiValueMap<String, HttpEntity<?>> buildMultipart(org.springframework.web.multipart.MultipartFile file,
                                                         String uploader) {
        MultipartBodyBuilder mpb = new MultipartBodyBuilder();
        try {
            mpb.part("file", new ByteArrayResource(file.getBytes()))
               .filename(file.getOriginalFilename())
               .contentType(MediaType.parseMediaType(file.getContentType()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mpb.part("uploader", uploader);
        return mpb.build();
    }

    public FileInfoResponse uploadProductFile(org.springframework.web.multipart.MultipartFile file,
                                              String uploader, Long productId) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/files/public/{id}")
                        .queryParam("type", "product")
                        .build(productId))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAuthToken())
                .body(BodyInserters.fromMultipartData(buildMultipart(file, uploader)))
                .retrieve()
                .bodyToMono(FileInfoResponse.class)
                .block();
    }

    public FileInfoResponse uploadReceiptFile(org.springframework.web.multipart.MultipartFile file,
                                              String uploader, Long orderId) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/files/public/{id}")
                        .queryParam("type", "receipt")
                        .build(orderId))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAuthToken())
                .body(BodyInserters.fromMultipartData(buildMultipart(file, uploader)))
                .retrieve()
                .bodyToMono(FileInfoResponse.class)
                .block();
    }

    
    public void deleteFile(String driveFileId) {
        if (driveFileId == null || driveFileId.isBlank()) return;

        webClient.delete()
                .uri("/api/files/{driveId}", driveFileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAuthToken())
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}