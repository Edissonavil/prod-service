package com.aec.prodsrv.client;

import com.aec.prodsrv.dto.StoredFileDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;

@Component
public class FileClient {

    private final WebClient webClient;

    public FileClient(
            @Value("${file-service.base-url}") String baseUrl,
            WebClient.Builder builder
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    private String getAuthToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new IllegalStateException("No hay usuario autenticado");
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        if (auth.getCredentials() instanceof String creds && !creds.isBlank()) {
            return creds;
        }
        throw new IllegalStateException("No se encontró JWT en el SecurityContext");
    }

    public StoredFileDto uploadProductFile(org.springframework.web.multipart.MultipartFile file,
                                         String uploader,
                                         Long productId) {
        if (file.isEmpty()) return null;

        MultipartBodyBuilder mpb = new MultipartBodyBuilder();
        try {
            mpb.part("file", new ByteArrayResource(file.getBytes()))
               .filename(file.getOriginalFilename())
               .contentType(MediaType.parseMediaType(file.getContentType()));
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo bytes del fichero: " + e.getMessage(), e);
        }
        mpb.part("uploader", uploader);

        return webClient.post()
                .uri("/api/files/public/{entityId}?type=product", productId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAuthToken())
                .bodyValue(mpb.build())
                .retrieve()
                .bodyToMono(StoredFileDto.class)
                .block();
    }

    public byte[] downloadFile(String driveFileId) {
        return webClient.get()
                .uri("/api/files/{driveFileId}", driveFileId)   // En file-service es /api/files/{driveId}
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    public void deleteFile(String driveFileId) {
        webClient.delete()
                .uri("/api/files/{driveFileId}", driveFileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAuthToken())
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
