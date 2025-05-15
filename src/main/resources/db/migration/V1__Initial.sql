CREATE TYPE status_enum AS ENUM (
  'ACTIVE',
  'INACTIVE'
);

CREATE TABLE IF NOT EXISTS "app_role" (
  "id" uuid PRIMARY KEY,
  "name" VARCHAR(100) UNIQUE NOT NULL,
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP NOT NULL,
  "status" status_enum DEFAULT 'ACTIVE',
  "deleted_at" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "app_user" (
  "id" uuid PRIMARY KEY,
  "role_id" uuid NOT NULL REFERENCES app_role(id),
  "first_name" VARCHAR(100) NOT NULL,
  "last_name" VARCHAR(100) NOT NULL,
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP NOT NULL,
  "status" status_enum DEFAULT 'ACTIVE',
  "deleted_at" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "app_user_credential" (
  "id" uuid PRIMARY KEY,
  "user_id" uuid NOT NULL REFERENCES app_user(id),
  "username" VARCHAR(100) NOT NULL,
  "plain_password" VARCHAR(255) NOT NULL,
  "encrypted_password" VARCHAR(255) NOT NULL,
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP NOT NULL,
  "status" status_enum DEFAULT 'ACTIVE',
  "deleted_at" TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "app_user_token" (
  "id" uuid PRIMARY KEY,
  "user_id" uuid NOT NULL REFERENCES app_user(id),
  "access_token" VARCHAR(255) UNIQUE NOT NULL,
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP NOT NULL,
  "status" status_enum DEFAULT 'ACTIVE',
  "deleted_at" TIMESTAMP
);
