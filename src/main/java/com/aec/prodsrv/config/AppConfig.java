package com.aec.prodsrv.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebClient webClient(
        WebClient.Builder builder,
        @Value("${filesrv.url:\"https://aecf-production.up.railway.app\",\n" + //
                        "      \"https://aecblock.com\"}") String baseUrl  
        ) {
        return builder
            .baseUrl(baseUrl)                      
            .build();
    }

     @Bean(name = "usersRestTemplate")
    public RestTemplate usersRestTemplate(
            RestTemplateBuilder builder,
            @Value("${users.service.url}") String usersServiceRootUri,
            @Value("${http.client.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${http.client.read-timeout-ms:5000}") long readTimeoutMs) {

        return builder
                .rootUri(usersServiceRootUri.trim())
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
