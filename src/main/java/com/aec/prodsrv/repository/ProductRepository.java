package com.aec.prodsrv.repository;

import com.aec.prodsrv.model.Product;
import com.aec.prodsrv.model.ProductStatus;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;



public interface ProductRepository extends JpaRepository<Product, Long> {
    // Listar productos por uploader
    Page<Product> findByUploaderUsername(String uploaderUsername, Pageable pg);
    Page<Product> findByEstado(ProductStatus estado, Pageable pg);
    List<Product> findByUploaderUsername(String uploaderUsername);
}


