CREATE TABLE member (
    id           BIGSERIAL    PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    name         VARCHAR(30)  NOT NULL,
    phone        VARCHAR(11)  NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    skin_type    VARCHAR(20),
    skin_tone    VARCHAR(20),
    withdrawn_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE member_skin_concern (
    member_id BIGINT      NOT NULL REFERENCES member (id),
    concern   VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, concern)
);
