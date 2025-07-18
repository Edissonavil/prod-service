package com.aec.prodsrv.model;

import jakarta.persistence.*;
import lombok.*;

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
    @Column(name = "fotografia_prod", length = 5000) // <-- Si esto almacena Base64, es el culpable
private String fotografiaProd;

@Column(name = "archivos_aut", length = 50000) // <-- Si esto almacena una lista de archivos, es el culpable
    private List<String> archivosAut;
    private String pais;

    @Enumerated(EnumType.STRING)
    private ProductStatus estado; // PENDIENTE, APROBADO, RECHAZADO

    private String uploaderUsername;
    private String usuarioDecision;
    private String comentario;

    // --- RELACIONES MANY-TO-MANY ---

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}) // Es importante que Persist y Merge estén aquí
    @JoinTable(
        name = "product_categories", // Nombre de la tabla intermedia para categorías
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categorias = new HashSet<>(); // Inicializa para evitar NPE

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}) // Para especialidades también
    @JoinTable(
        name = "product_specialties", // Nombre de la tabla intermedia para especialidades
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "specialty_id")
    )
    private Set<Category> especialidades = new HashSet<>(); // Inicializa para evitar NPE
}