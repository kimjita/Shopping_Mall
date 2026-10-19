-- 낙관적 락 비교 실험용 컬럼이다. 운영 스키마와 Sku 엔티티에는 두지 않는다.
alter table sku add column version bigint not null default 0;
