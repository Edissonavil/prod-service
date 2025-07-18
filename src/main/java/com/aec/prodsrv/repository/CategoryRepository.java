package com.aec.prodsrv.repository;

import com.aec.prodsrv.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category,Long> {
    Optional<Category> findByNombreIgnoreCase(String nombre);
  }
  