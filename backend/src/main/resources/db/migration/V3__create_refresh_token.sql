CREATE TABLE refresh_token (
    id         BIGSERIAL   PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES member (id),
    family_id  UUID        NOT NULL,
    token_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_token_member_id ON refresh_token (member_id);
CREATE INDEX idx_refresh_token_family_id ON refresh_token (family_id);
