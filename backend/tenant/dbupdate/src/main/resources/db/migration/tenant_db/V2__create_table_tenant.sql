CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE tenant
(
    id          UUID    NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    name        VARCHAR(255),
    description VARCHAR(255),
    active      BOOLEAN NOT NULL,
    owner_id    UUID NOT NULL ,
    CONSTRAINT pk_tenant PRIMARY KEY (id)
);
