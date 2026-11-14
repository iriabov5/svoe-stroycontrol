-- Схема итерации 1. Идентификаторы назначает приложение, пароль хранится только как хеш.
-- CHECK не может подзапросом убедиться, что contractor_id указывает на роль CONTRACTOR:
-- это правило остаётся в модуле projects.

CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL,
    role varchar(16) NOT NULL,
    password_hash varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT accounts_email_key UNIQUE (email),
    CONSTRAINT accounts_email_normalized CHECK (
        char_length(email) BETWEEN 1 AND 254
        AND email = lower(email)
        AND email = btrim(email)
        AND email ~ '^[^[:space:]@]+@[^[:space:]@.]+(\.[^[:space:]@.]+)+$'
    ),
    CONSTRAINT accounts_role_known CHECK (role IN ('CUSTOMER', 'CONTRACTOR')),
    CONSTRAINT accounts_password_hash_present CHECK (char_length(password_hash) > 0)
);

CREATE TABLE house_projects (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    address varchar(300) NOT NULL,
    customer_id uuid NOT NULL,
    contractor_id uuid,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT house_projects_customer_id_fkey
        FOREIGN KEY (customer_id) REFERENCES accounts (id),
    CONSTRAINT house_projects_contractor_id_fkey
        FOREIGN KEY (contractor_id) REFERENCES accounts (id),
    CONSTRAINT house_projects_contractor_not_owner CHECK (
        contractor_id IS NULL OR contractor_id <> customer_id
    ),
    CONSTRAINT house_projects_name_present CHECK (
        char_length(name) BETWEEN 1 AND 120
        AND name = btrim(name)
    ),
    CONSTRAINT house_projects_address_present CHECK (
        char_length(address) BETWEEN 1 AND 300
        AND address = btrim(address)
    )
);

CREATE INDEX house_projects_customer_id_idx ON house_projects (customer_id);
CREATE INDEX house_projects_contractor_id_idx ON house_projects (contractor_id);
