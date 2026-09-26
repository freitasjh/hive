-- Alter agent table
ALTER TABLE agent ADD COLUMN prompt_template_id UUID;
ALTER TABLE agent ADD COLUMN prompt_template_metadata JSONB;
ALTER TABLE agent DROP COLUMN IF EXISTS prompt;

-- Add foreign key for prompt_template
ALTER TABLE agent ADD CONSTRAINT fk_agent_prompt_template 
FOREIGN KEY (prompt_template_id) REFERENCES prompt_template(id);

-- Alter chat_message table
ALTER TABLE chat_message ADD COLUMN metadata JSONB;

-- Create chat_session table
CREATE TABLE chat_session
(
    id          UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    agent_id    UUID,
    title       VARCHAR(255),
    summary     VARCHAR(2000),

    CONSTRAINT pk_chat_session PRIMARY KEY (id)
);

-- Add foreign key from chat_message to chat_session
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_session 
FOREIGN KEY (session_id) REFERENCES chat_session(id);
