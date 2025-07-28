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
    private String fotografiaProd; 
    private String fotografiaUrl;  
    private List<String> archivosAut; 
    private List<String> archivosAutUrls; 
    private String estado;
    private List<String> categorias;
    private List<String> especialidades;
    private String pais;
    private String uploaderUsername;
    private String usuarioDecision;
    private String comentario;

}