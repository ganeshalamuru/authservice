CREATE TABLE IF NOT EXISTS app_role
(
    id                    SERIAL PRIMARY KEY,
    name                  VARCHAR(50)       NOT NULL UNIQUE
);

INSERT INTO app_role (name)
VALUES
       ('USER'),
       ('ADMIN');


CREATE TABLE IF NOT EXISTS app_user
(
    id                    SERIAL PRIMARY KEY,
    username              VARCHAR(100)      NOT NULL UNIQUE,
    password              VARCHAR(255)      NOT NULL,
    role_id               BIGINT            NOT NULL REFERENCES app_role (id),
    first_name            VARCHAR(100)      NOT NULL,
    last_name             VARCHAR(100)      NOT NULL
);
