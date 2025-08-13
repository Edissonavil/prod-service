package com.aec.prodsrv.config;

import org.springframework.beans.factory.annotation.Value;
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
}
