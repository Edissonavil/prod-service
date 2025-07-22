// src/main/java/com/aec/prodsrv/security/CustomUserDetailsService.java
package com.aec.prodsrv.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final RestTemplate restTemplate;

    @Value("${users.service.url}")
    private String usersServiceUrl;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String url = String.format("%s/api/users/%s", usersServiceUrl, username);
        UserDto user;
        try {
            user = restTemplate.getForObject(url, UserDto.class);
        } catch (RestClientException ex) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username, ex);
        }
        if (user == null) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }
        List<GrantedAuthority> authorities = user.getRoles().stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        return User.builder()
                   .username(user.getUsername())
                   .password("")      
                   .authorities(authorities)
                   .accountExpired(false)
                   .accountLocked(false)
                   .credentialsExpired(false)
                   .disabled(false)
                   .build();
    }

    /**
     * DTO interno para mapear la respuesta de users-service.
     */
    public static class UserDto {
        private String username;
        private List<String> roles;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    
}
