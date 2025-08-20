// src/main/java/com/aec/prodsrv/client/UsersClient.java
package com.aec.prodsrv.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.util.Map;
import java.util.Optional;
@Slf4j@Component
public class UsersClient {

    private final RestTemplate rt;

    public UsersClient(@Qualifier("usersRestTemplate") RestTemplate rt) {
        this.rt = rt;
    }

    @PostConstruct
    void logBase(@Value("${users.service.url}") String usersBaseUrl) {
        // Para verificar en logs el root configurado (con puerto 8081)
        log.info("[UsersClient] users.service.url = {}", usersBaseUrl);
    }

    @SuppressWarnings("unchecked")
    public Optional<String> findEmailByUsername(String username) {
        String[] paths = new String[] {
            "/by-username/{username}",
            "/{username}"
        };

        for (String path : paths) {
            try {
                log.info("[UsersClient] GET (relative) {}", path);
                ResponseEntity<Map> resp = rt.getForEntity(path, Map.class, username);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    Object email = resp.getBody().get("email");
                    log.info("[UsersClient] Resuelto email='{}' para username='{}' vía {}",
                             email, username, path);
                    return Optional.ofNullable(email != null ? email.toString() : null);
                } else {
                    log.warn("[UsersClient] Respuesta no OK {}: {}", path, resp.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("[UsersClient] Falló {} -> {}", path, e.toString());
            }
        }
        log.warn("[UsersClient] No se pudo resolver email para '{}'", username);
        return Optional.empty();
    }
}
