package com.aec.prodsrv.config; // Asegúrate de que el paquete sea correcto

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Añade esta si no está
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSecurity // Importante para la seguridad web
@EnableMethodSecurity // Asegúrate de que esté para seguridad a nivel de método
public class SecurityConfig {

  @Value("${jwt.secret}") // Asegúrate de que esta propiedad esté definida en application.properties/yml
  private String jwtSecret;

  @Bean
  public JwtDecoder jwtDecoder() {
    byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
    SecretKey key   = new SecretKeySpec(keyBytes, "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).build();
  }

  @Bean 
  public JwtAuthenticationConverter jwtAuthenticationConverter() { // Cambiado a public si lo necesitas como bean
    JwtGrantedAuthoritiesConverter ga = new JwtGrantedAuthoritiesConverter();
    ga.setAuthorityPrefix("");          // no “SCOPE_”
    ga.setAuthoritiesClaimName("role"); // tu claim

    JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(ga);
    return conv;
  }


  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(auth -> auth
            // Rutas de autenticación (login/registro) - suelen ser públicas
            .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/products").permitAll() // Para getAllProducts
            .requestMatchers(HttpMethod.GET, "/api/products/{id}").permitAll() // Para byId (producto individual)

            .requestMatchers("/error").permitAll()

            // Rutas protegidas (Colaborador)
            .requestMatchers(HttpMethod.POST, "/api/products").hasAuthority("ROL_COLABORADOR") // Crear producto
            .requestMatchers(HttpMethod.PUT, "/api/products/{id}").hasAuthority("ROL_COLABORADOR") // Actualizar producto
            .requestMatchers(HttpMethod.DELETE, "/api/products/{id}").hasAuthority("ROL_COLABORADOR") // Eliminar producto
            .requestMatchers(HttpMethod.GET, "/api/products/my-products").hasAuthority("ROL_COLABORADOR") // Mis productos

            // Rutas protegidas (Admin)
            .requestMatchers(HttpMethod.GET, "/api/products/pending").hasAuthority("ROL_ADMIN") // Productos pendientes
            .requestMatchers(HttpMethod.PUT, "/api/products/{id}/decision").hasAuthority("ROL_ADMIN") // Decidir sobre producto

            // Cualquier otra petición que no haya sido permitida explícitamente antes, REQUIERE autenticación
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        );
    return http.build();
  }

  @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedOrigins(List.of("https://gateway-production-129e.up.railway.app"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Aplica a todas las rutas
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}