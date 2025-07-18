-- 1) Crear tabla categories
CREATE TABLE categories (
  id   BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  nombre VARCHAR(100) NOT NULL UNIQUE
);

-- 2) Añadir columnas a products
ALTER TABLE products
  ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  ADD COLUMN pais   VARCHAR(100) NOT NULL,
  ADD COLUMN categoria_id BIGINT NOT NULL;

-- 3) Foreign key
ALTER TABLE products
  ADD CONSTRAINT fk_products_categories
    FOREIGN KEY (categoria_id) REFERENCES categories(id);
