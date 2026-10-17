CREATE TABLE address (
    id         BIGSERIAL    PRIMARY KEY,
    member_id  BIGINT       NOT NULL REFERENCES member (id),
    recipient  VARCHAR(30)  NOT NULL,
    phone      VARCHAR(11)  NOT NULL,
    zip_code   CHAR(5)      NOT NULL,
    address1   VARCHAR(200) NOT NULL,
    address2   VARCHAR(200),
    is_default BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_address_member_id ON address (member_id);
