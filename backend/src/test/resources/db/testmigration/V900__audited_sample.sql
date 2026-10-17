create table audited_sample (
    id         bigserial primary key,
    name       varchar(50),
    created_at timestamptz not null,
    updated_at timestamptz not null
);
