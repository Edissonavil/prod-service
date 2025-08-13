package com.aec.prodsrv.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsersClient {

    private final RestTemplate restTemplate;

    @Value("${users.service.url}")
    private String usersBaseUrl; 
    /**
     * Intenta obtener el email del usuario por username desde el servicio de usuarios.
     * Se asume un endpoint tipo GET /api/users/{username} que retorna JSON con campo "email".
     */
    public Optional<String> findEmailByUsername(String username) {
        try {
            String url = usersBaseUrl + "/" + username;
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object email = resp.getBody().get("email");
                return Optional.ofNullable(email != null ? email.toString() : null);
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener email para username {}: {}", username, e.getMessage());
        }
        return Optional.empty();
    }
}
