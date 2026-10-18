package com.sprint.mission.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 상품 카테고리다. 마이그레이션 시드로만 채우는 읽기 전용 엔티티이며 최대 3단계다. */
@Getter
@Entity
@Immutable
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    private Long id;

    @Column
    private Long parentId;

    @Column(nullable = false, length = 30)
    private String name;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int depth;

    @Column(nullable = false)
    private int sortOrder;
}
