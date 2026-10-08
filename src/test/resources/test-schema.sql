CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    is_active   BOOLEAN      DEFAULT TRUE,
    created_by  VARCHAR(255),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255)
);