package com.sprint.mission.product.repository;

import static com.sprint.mission.product.domain.QBrand.brand;
import static com.sprint.mission.product.domain.QProduct.product;
import static com.sprint.mission.product.domain.QSku.sku;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.AdminProductSearchCondition;
import com.sprint.mission.product.dto.AdminProductSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 관리자 상품 목록이다. 상태와 상관없이 id 내림차순으로 읽는다. 상품·브랜드·대표 이미지는 조인 하나로,
 * 옵션 수와 최소 재고는 스칼라 서브쿼리로 읽으므로 쿼리 수가 페이지 크기와 무관하다.
 */
@Repository
@RequiredArgsConstructor
public class AdminProductSearchRepository {

    /** 이 수량 이하인 옵션이 있는 판매 중 상품을 재고 부족으로 본다. 대시보드도 같은 기준을 쓴다. */
    public static final int LOW_STOCK_THRESHOLD = 5;

    private static final StringPath IMAGE = Expressions.stringPath("img");

    private final JPAQueryFactory queryFactory;

    /** cursorId보다 작은 id의 상품을 limit개까지 읽는다. cursorId가 null이면 처음부터 읽는다. now는 세일 진행 여부 판단에 쓴다. */
    public List<AdminProductSummary> search(AdminProductSearchCondition condition, Long cursorId, Instant now,
            int limit) {
        BooleanExpression saleActive = product.sale.rate.isNotNull()
                .and(product.sale.startsAt.loe(now))
                .and(product.sale.endsAt.gt(now));

        List<Tuple> tuples = queryFactory
                .select(product.id, product.name, brand.name, IMAGE, product.status, product.basePrice,
                        product.sale.rate, saleActive,
                        JPAExpressions.select(sku.count()).from(sku).where(sku.productId.eq(product.id)),
                        Expressions.numberTemplate(Integer.class, "coalesce({0}, 0)",
                                JPAExpressions.select(sku.stock.min()).from(sku).where(sku.productId.eq(product.id))),
                        product.createdAt)
                .from(product)
                .join(brand).on(brand.id.eq(product.brandId))
                .leftJoin(product.imageUrls, IMAGE)
                .on(Expressions.numberTemplate(Integer.class, "index({0})", IMAGE).eq(0))
                .where(conditions(condition, cursorId))
                .orderBy(product.id.desc())
                .limit(limit)
                .fetch();

        return tuples.stream().map(t -> new AdminProductSummary(t.get(0, Long.class), t.get(1, String.class),
                t.get(2, String.class), t.get(3, String.class), t.get(4, ProductStatus.class), t.get(5, Long.class),
                t.get(6, Integer.class), Boolean.TRUE.equals(t.get(7, Boolean.class)),
                t.get(8, Long.class).intValue(), t.get(9, Integer.class), t.get(10, Instant.class))).toList();
    }

    private Predicate[] conditions(AdminProductSearchCondition condition, Long cursorId) {
        List<Predicate> where = new ArrayList<>();
        if (condition.keyword() != null) {
            where.add(Expressions.booleanTemplate("{0} ilike {1} escape '\\'",
                    product.searchText, "%" + escapeLike(condition.keyword()) + "%"));
        }
        if (condition.status() != null) {
            where.add(product.status.eq(condition.status()));
        }
        if (condition.brandId() != null) {
            where.add(product.brandId.eq(condition.brandId()));
        }
        if (condition.onlyLowStock()) {
            where.add(product.status.eq(ProductStatus.ON_SALE));
            where.add(JPAExpressions.selectOne().from(sku)
                    .where(sku.productId.eq(product.id), sku.stock.loe(LOW_STOCK_THRESHOLD)).exists());
        }
        if (cursorId != null) {
            where.add(product.id.lt(cursorId));
        }
        return where.toArray(Predicate[]::new);
    }

    /** LIKE 패턴에서 특별한 의미를 갖는 {@code \}, {@code %}, {@code _}를 글자 그대로 찾도록 이스케이프한다. */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
