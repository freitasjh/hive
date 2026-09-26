CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Remove duplicatas mantendo registro mais antigo (menor create_at, fallback menor id)
DELETE FROM users WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY username ORDER BY create_at NULLS FIRST, id) AS rn
        FROM users WHERE username IS NOT NULL
    ) t WHERE rn > 1
);

DELETE FROM users WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY email ORDER BY create_at NULLS FIRST, id) AS rn
        FROM users WHERE email IS NOT NULL
    ) t WHERE rn > 1
);

DELETE FROM users WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY federal_id ORDER BY create_at NULLS FIRST, id) AS rn
        FROM users WHERE federal_id IS NOT NULL
    ) t WHERE rn > 1
);

CREATE UNIQUE INDEX uk_users_username ON users(username) WHERE username IS NOT NULL;
CREATE UNIQUE INDEX uk_users_email ON users(email) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uk_users_federal_id ON users(federal_id) WHERE federal_id IS NOT NULL;
