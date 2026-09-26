CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE agent
(
    id            UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at     TIMESTAMP WITHOUT TIME ZONE,
    update_at     TIMESTAMP WITHOUT TIME ZONE,
    tenant_id     UUID,
    name          VARCHAR(255),
    description   VARCHAR(255),
    prompt        VARCHAR(4000),
    llm_model     VARCHAR(255),
    provider_type VARCHAR(255),
    temperature   DOUBLE PRECISION,
    active        BOOLEAN DEFAULT TRUE,
    version       INT DEFAULT 1,

    CONSTRAINT pk_agent PRIMARY KEY (id)
);

CREATE TABLE chat_message
(
    id          UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    tenant_id   UUID,
    session_id  UUID NOT NULL,
    role        VARCHAR(255) NOT NULL,
    content     VARCHAR(8000),

    CONSTRAINT pk_chat_message PRIMARY KEY (id)
);

CREATE TABLE prompt_template
(
    id          UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    tenant_id   UUID,
    name        VARCHAR(255) NOT NULL,
    template    VARCHAR(8000) NOT NULL,
    version     INT DEFAULT 1,

    CONSTRAINT pk_prompt_template PRIMARY KEY (id)
);
