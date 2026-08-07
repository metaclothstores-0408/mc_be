--liquibase formatted sql
--changeset metacloth:001

CREATE TYPE auth_provider_enum AS ENUM ('GOOGLE', 'PHONE');
CREATE TYPE order_status_enum AS ENUM ('PLACED', 'IN_PRINTING', 'SHIPPED', 'DELIVERED', 'CANCELLED');
CREATE TYPE garment_type_enum AS ENUM ('ROUND_NECK', 'OVERSIZED', 'HOODIE', 'ACID_WASH', 'SPORTS');
CREATE TYPE user_role_enum AS ENUM ('CUSTOMER', 'ADMIN');

CREATE TABLE avatars (
  id BIGSERIAL PRIMARY KEY, title VARCHAR(100) NOT NULL,
  image_url VARCHAR(500) NOT NULL, category VARCHAR(50) NOT NULL DEFAULT 'PRESET_PHASE_1',
  is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY, full_name VARCHAR(100), email VARCHAR(150) UNIQUE,
  phone_number VARCHAR(20) UNIQUE, password_hash VARCHAR(255),
  auth_provider auth_provider_enum NOT NULL, role user_role_enum NOT NULL DEFAULT 'CUSTOMER',
  selected_avatar_id BIGINT REFERENCES avatars(id) ON DELETE SET NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_user_identity CHECK (email IS NOT NULL OR phone_number IS NOT NULL)
);

CREATE TABLE products (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(150) NOT NULL, description TEXT,
  garment_type garment_type_enum NOT NULL, base_price NUMERIC(10,2) NOT NULL,
  print_surcharge NUMERIC(10,2) NOT NULL DEFAULT 0, is_customizable BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_variants (
  id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  color_name VARCHAR(50) NOT NULL, color_hex VARCHAR(10) NOT NULL, size VARCHAR(10) NOT NULL,
  stock_quantity INT NOT NULL DEFAULT 0, mockup_front_url VARCHAR(500) NOT NULL,
  mockup_back_url VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT unique_variant UNIQUE(product_id,color_hex,size)
);

CREATE TABLE orders (
  id BIGSERIAL PRIMARY KEY, order_number VARCHAR(50) UNIQUE NOT NULL,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  total_amount NUMERIC(10,2) NOT NULL, order_status order_status_enum NOT NULL DEFAULT 'PLACED',
  shipping_name VARCHAR(100) NOT NULL, shipping_phone VARCHAR(20) NOT NULL,
  shipping_address_line TEXT NOT NULL, city VARCHAR(100) NOT NULL, postal_code VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
  id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_variant_id BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
  avatar_id BIGINT REFERENCES avatars(id) ON DELETE SET NULL, quantity INT NOT NULL,
  unit_price NUMERIC(10,2) NOT NULL, print_config_json JSONB, preview_render_url VARCHAR(500)
);

CREATE TABLE otp_requests (
  id BIGSERIAL PRIMARY KEY, phone_number VARCHAR(20) NOT NULL, otp_hash VARCHAR(255) NOT NULL,
  purpose VARCHAR(30) NOT NULL DEFAULT 'LOGIN', expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ, attempt_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_products_garment_type ON products(garment_type);
CREATE INDEX idx_variant_lookup ON product_variants(product_id,color_hex,size);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_otp_phone_created ON otp_requests(phone_number,created_at DESC);

--changeset metacloth:002 splitStatements:false

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_products_updated_at
BEFORE UPDATE ON products
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_variants_updated_at
BEFORE UPDATE ON product_variants
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
