package com.aec.prodsrv.dto; // Asegúrate de que el paquete sea correcto

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private Long idProducto;
    private String nombre;
    private String descripcionProd;
    private Double precioIndividual;
    private String fotografiaProd; // Nombre del archivo (ej. UUID.jpeg)
    private String fotografiaUrl;  // <- Nuevo campo para la URL completa
    private List<String> archivosAut; // Nombres de los archivos autorizados
    private List<String> archivosAutUrls; // <- Nuevo campo para las URLs completas de los archivos autorizados
    private String estado;
    private List<String> categorias;
    private List<String> especialidades;
    private String pais;
    private String uploaderUsername;
    private String usuarioDecision;
    private String comentario;

}