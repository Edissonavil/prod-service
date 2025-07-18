package com.aec.prodsrv.client;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.aec.prodsrv.security.JwtAuthenticationFilter.CustomWebAuthenticationDetails;

import io.jsonwebtoken.Jwt;

import java.io.IOException;
import java.util.Map;

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
    if (auth == null) {
        throw new IllegalStateException("No hay usuario autenticado en el SecurityContext");
    }
    // 1) Desde CustomWebAuthenticationDetails
    if (auth.getDetails() instanceof CustomWebAuthenticationDetails c) {
        return c.getJwtToken();
    }
    // 2) Desde credentials
    if (auth.getCredentials() instanceof String creds && !creds.isBlank()) {
        return creds;
    }
    // 3) Si use un JwtAuthenticationToken (resource-server)
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
        return jwtAuth.getToken().getTokenValue();
    }
    throw new IllegalStateException("No se encontró JWT en el SecurityContext");
}


    public Map<String,Object> uploadProductFile(MultipartFile file, String uploader, Long productId) {
    if (file.isEmpty()) {
        return Map.of("filename", null, "originalFilename", null);
    }

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
    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAuthToken()) // ✅ AÑADIDO
    .bodyValue(mpb.build())
    .retrieve()
    .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
    .block();

}


    public byte[] downloadFile(Long entityId, String filename) {
        return webClient.get()
            .uri("/api/files/{entityId}/{filename}", entityId, filename)
            .retrieve()
            .bodyToMono(byte[].class)
            .block();
    }

    public void deleteFile(Long entityId, String filename) {
        String token = getAuthToken();
        webClient.delete()
            .uri("/api/files/{entityId}/{filename}", entityId, filename)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}

