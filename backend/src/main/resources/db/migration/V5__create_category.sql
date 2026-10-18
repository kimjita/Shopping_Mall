CREATE TABLE category (
    id         BIGSERIAL   PRIMARY KEY,
    parent_id  BIGINT      REFERENCES category (id),
    name       VARCHAR(30) NOT NULL,
    depth      SMALLINT    NOT NULL CHECK (depth BETWEEN 1 AND 3),
    sort_order INT         NOT NULL
);

CREATE INDEX idx_category_parent_id ON category (parent_id);

-- 시드 데이터다. 관리자 CRUD 없이 마이그레이션으로만 바꾼다.
INSERT INTO category (id, parent_id, name, depth, sort_order) VALUES
    (1, NULL, '스킨케어', 1, 1),
    (2, NULL, '마스크팩', 1, 2),
    (3, NULL, '클렌징', 1, 3),
    (4, NULL, '선케어', 1, 4),
    (5, NULL, '메이크업', 1, 5),
    (6, NULL, '바디케어', 1, 6),
    (7, NULL, '헤어케어', 1, 7),

    (8, 1, '토너', 2, 1),
    (9, 1, '에센스·세럼', 2, 2),
    (10, 1, '크림', 2, 3),
    (11, 1, '로션', 2, 4),
    (12, 1, '미스트·오일', 2, 5),

    (13, 2, '시트팩', 2, 1),
    (14, 2, '패드', 2, 2),
    (15, 2, '워시오프팩', 2, 3),

    (16, 3, '폼·젤', 2, 1),
    (17, 3, '오일·밤', 2, 2),
    (18, 3, '워터·밀크', 2, 3),
    (19, 3, '필링·스크럽', 2, 4),

    (20, 4, '선크림', 2, 1),
    (21, 4, '선스틱', 2, 2),
    (22, 4, '선쿠션', 2, 3),

    (23, 5, '립', 2, 1),
    (24, 5, '베이스', 2, 2),
    (25, 5, '아이', 2, 3),

    (26, 6, '바디로션', 2, 1),
    (27, 6, '바디워시', 2, 2),
    (28, 6, '핸드크림', 2, 3),

    (29, 7, '샴푸', 2, 1),
    (30, 7, '트리트먼트', 2, 2),
    (31, 7, '헤어에센스', 2, 3),

    (32, 23, '립스틱', 3, 1),
    (33, 23, '립틴트', 3, 2),
    (34, 23, '립글로스', 3, 3),

    (35, 24, '쿠션', 3, 1),
    (36, 24, '파운데이션', 3, 2),
    (37, 24, '프라이머', 3, 3),

    (38, 25, '아이섀도', 3, 1),
    (39, 25, '아이라이너', 3, 2),
    (40, 25, '마스카라', 3, 3);

SELECT setval('category_id_seq', (SELECT MAX(id) FROM category));
