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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class UsersClient {

    private static final Logger log = LoggerFactory.getLogger(UsersClient.class);

    private final RestTemplate rt;

    @Value("${users.service.url}")
    private String usersBaseUrl; // p.ej: http://users-service.railway.internal:8081/api/users

    public UsersClient(@Qualifier("usersRestTemplate") RestTemplate rt) {
        this.rt = rt;
    }

    @PostConstruct
    void logBase() { // <- SIN parámetros
        log.info("[UsersClient] users.service.url = {}", usersBaseUrl);
    }

    @SuppressWarnings("unchecked")
    public Optional<String> findEmailByUsername(String username) {
        // Como el RestTemplate tiene rootUri=users.service.url, usamos paths relativos:
        String[] paths = new String[] {
            "/by-username/{username}",
            "/{username}"
        };

        for (String path : paths) {
            try {
                log.info("[UsersClient] GET {}", path);
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
