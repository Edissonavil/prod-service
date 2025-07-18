package com.aec.prodsrv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Esto configura CORS a nivel de DispatcherServlet (Spring MVC).
     * Se aplica a todas las rutas /api/**.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                    "http://localhost:3000",
                    "https://tu-front-domain.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                ;
    }

    /**
     * Y este bean le dice a Spring Security que use la misma configuración.
     * Solo es necesario si tienes Spring Security activo con http.cors().
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(List.of("http://localhost:3000"));
      config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
      config.setAllowedHeaders(List.of("*"));
      config.setAllowCredentials(true);    // si tu cliente envía cookies o Auth headers
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", config);
      return source;
    }
}

