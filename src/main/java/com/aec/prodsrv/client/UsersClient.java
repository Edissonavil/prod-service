// src/main/java/com/aec/prodsrv/client/UsersClient.java
package com.aec.prodsrv.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class UsersClient {

    private final RestTemplate rt;

    @Value("${users.service.url}")
    private String usersBaseUrl; // p.ej: http://users-service.railway.internal:8081/api/users

    public UsersClient(@Qualifier("usersRestTemplate") RestTemplate rt) {
        this.rt = rt;
    }

    @SuppressWarnings("unchecked")
    public Optional<String> findEmailByUsername(String username) {
        // 1) /api/users/{username}
        String url1 = usersBaseUrl.endsWith("/") ? usersBaseUrl + username : usersBaseUrl + "/" + username;
        // 2) /api/users/by-username/{username} (por si tu controlador usa esta ruta)
        String url2 = usersBaseUrl.endsWith("/") ? usersBaseUrl + "by-username/" + username
                                                 : usersBaseUrl + "/by-username/" + username;

        for (String url : new String[]{url1, url2}) {
            try {
                log.info("[UsersClient] GET {}", url);
                ResponseEntity<Map> resp = rt.getForEntity(url, Map.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    Object email = resp.getBody().get("email");
                    log.info("[UsersClient] Resuelto email='{}' para username='{}' vía {}", email, username, url);
                    return Optional.ofNullable(email != null ? email.toString() : null);
                } else {
                    log.warn("[UsersClient] Respuesta no OK {}: {}", url, resp.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("[UsersClient] Falló {} -> {}", url, e.toString());
            }
        }
        log.warn("[UsersClient] No se pudo resolver email para '{}'", username);
        return Optional.empty();
    }
}
