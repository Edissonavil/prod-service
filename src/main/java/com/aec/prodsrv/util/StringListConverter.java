// src/main/java/com/aec/prodsrv/config/converter/StringListConverter.java
package com.aec.prodsrv.util; // Ajusta este paquete según tu estructura

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Converter // Esta anotación es crucial para que JPA la detecte
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(StringListConverter.class);

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            // Convierte la lista de Strings a una cadena JSON
            return objectMapper.writeValueAsString(attribute);
        } catch (IOException e) {
            log.error("Error al serializar List<String> a JSON para base de datos: {}", e.getMessage(), e);
            // Dependiendo de tu estrategia de errores, podrías lanzar una RuntimeException
            return null;
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // Convierte la cadena JSON de vuelta a una lista de Strings
            return objectMapper.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.error("Error al deserializar JSON de base de datos a List<String>: {}", e.getMessage(), e);
            // Dependiendo de tu estrategia de errores, podrías lanzar una RuntimeException
            return Collections.emptyList();
        }
    }
}