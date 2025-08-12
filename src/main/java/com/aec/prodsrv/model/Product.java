// src/main/java/com/aec/prodsrv/model/Product.java
package com.aec.prodsrv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.aec.prodsrv.util.StringListConverter; // ¡Importa la clase del convertidor!

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idProducto;

    private String nombre;

    @Column(name = "descripcion_prod", length = 5000)
    private String descripcionProd;

    private Double precioIndividual;

    @Column(name = "fotografia_prod", length = 50000)
    @Convert(converter = StringListConverter.class)
    private List<String> fotografiaProd; // Lista de IDs de Google Drive

    @Column(name = "archivos_aut", length = 50000)
    @Convert(converter = StringListConverter.class) 
    private List<String> archivosAut; 
    // ----------------------------

    private String pais;

    @Enumerated(EnumType.STRING)
    private ProductStatus estado; // PENDIENTE, APROBADO, RECHAZADO

    private String uploaderUsername;
    private String usuarioDecision;
    private String comentario;

    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "product_categories", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categorias = new HashSet<>();

    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "product_specialties", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "specialty_id"))
    private Set<Category> especialidades = new HashSet<>();
}