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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.aec.prodsrv.security.JwtAuthenticationFilter.CustomWebAuthenticationDetails;

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

    /**
     * Objeto de respuesta para la subida de archivos, conteniendo el ID de Google Drive y el nombre original.
     */
    public record UploadFileResponse(String googleDriveFileId, String originalFilename) {}


    public UploadFileResponse uploadProductFile(MultipartFile file, String uploader, Long productId) {
        if (file.isEmpty()) {
            return new UploadFileResponse(null, null); // Devolver un objeto con nulos si el archivo está vacío
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

        // La respuesta del FileService (desde el StoreFileController) DEBERÍA devolver
        // { "googleDriveFileId": "...", "originalName": "..." }
        // Necesitamos que el FileService devuelva un objeto con esas propiedades.
        // Si no lo hace, FileService debe ser modificado.
        return webClient.post()
            .uri("/api/files/public/{entityId}?type=product", productId)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAuthToken())
            .bodyValue(mpb.build())
            .retrieve()
            .bodyToMono(UploadFileResponse.class) // <-- Cambiado para esperar nuestro nuevo record
            .block();
    }

    // El método downloadFile ahora debe usar el googleDriveFileId
    public byte[] downloadFile(String googleDriveFileId) { // Cambiado Long entityId, String filename a String googleDriveFileId
        return webClient.get()
            .uri("/api/files/download/{googleDriveFileId}", googleDriveFileId) // Nueva ruta en FileService
            .retrieve()
            .bodyToMono(byte[].class)
            .block();
    }

    // El método deleteFile también debe usar el googleDriveFileId
    public void deleteFile(String googleDriveFileId) { // Cambiado Long entityId, String filename a String googleDriveFileId
        String token = getAuthToken();
        webClient.delete()
            .uri("/api/files/{googleDriveFileId}", googleDriveFileId) // Nueva ruta en FileService
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}