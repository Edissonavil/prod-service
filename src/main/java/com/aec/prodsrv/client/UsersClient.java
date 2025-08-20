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

    private String ensureAbsolute(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        return "http://" + url;
    }

    @SuppressWarnings("unchecked")
    public Optional<String> findEmailByUsername(String username) {
        String base = ensureAbsolute(usersBaseUrl.trim());
        String url1 = base.endsWith("/") ? base + username : base + "/" + username;
        String url2 = base.endsWith("/") ? base + "by-username/" + username
                                         : base + "/by-username/" + username;

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
