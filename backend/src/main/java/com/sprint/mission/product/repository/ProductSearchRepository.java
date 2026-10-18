package com.sprint.mission.product.repository;

import static com.sprint.mission.product.domain.QBrand.brand;
import static com.sprint.mission.product.domain.QProduct.product;
import static com.sprint.mission.product.domain.QSku.sku;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.ProductSearchCondition;
import com.sprint.mission.product.dto.ProductSort;
import com.sprint.mission.product.dto.ProductSummary;
import com.sprint.mission.product.service.ProductCursor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 공개 상품 목록 조회다. 상품·브랜드·대표 이미지와 품절 여부를 쿼리 한 번으로 읽으므로 쿼리 수가 페이지 크기와 무관하다.
 */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private static final StringPath IMAGE = Expressions.stringPath("img");

    private final JPAQueryFactory queryFactory;

    /** 목록 한 줄이다. 커서의 정렬값으로 쓰는 등록 시각을 응답 DTO와 따로 들고 있다. */
    public record Row(ProductSummary summary, Instant createdAt) {
    }

    /**
     * 조건에 맞는 상품을 정렬 순서대로 limit개까지 읽는다. 커서가 있으면 그 다음부터 읽는다.
     * leafIds가 null이면 카테고리를 거르지 않는다. now는 현재 판매가·세일 여부·할인율 판단에 모두 같은 값을 쓴다.
     */
    public List<Row> search(ProductSearchCondition condition, List<Long> leafIds, Instant now, int limit) {
        NumberExpression<Long> currentPrice = currentPrice(now);
        BooleanExpression saleActive = saleActive(now);
        BooleanExpression noStock = JPAExpressions.selectOne().from(sku)
                .where(sku.productId.eq(product.id), sku.stock.gt(0))
                .notExists();

        List<Tuple> tuples = queryFactory
                .select(product.id, product.name, brand.name, IMAGE, product.minPrice, currentPrice,
                        new CaseBuilder().when(saleActive).then(product.sale.rate)
                                .otherwise(Expressions.nullExpression(Integer.class)),
                        new CaseBuilder().when(noStock).then(true).otherwise(false),
                        product.createdAt)
                .from(product)
                .join(brand).on(brand.id.eq(product.brandId))
                .leftJoin(product.imageUrls, IMAGE)
                .on(Expressions.numberTemplate(Integer.class, "index({0})", IMAGE).eq(0))
                .where(conditions(condition, leafIds, now, currentPrice, saleActive))
                .orderBy(orderBy(condition.sort(), currentPrice))
                .limit(limit)
                .fetch();

        return tuples.stream().map(t -> new Row(new ProductSummary(
                t.get(product.id), t.get(product.name), t.get(brand.name), t.get(IMAGE),
                t.get(product.minPrice), t.get(currentPrice), t.get(6, Integer.class),
                Boolean.TRUE.equals(t.get(7, Boolean.class))), t.get(product.createdAt))).toList();
    }

    /**
     * 현재 판매가다. {@code Sale.apply}와 같은 순서로 계산해 정수 나눗셈 결과가 항상 같다.
     * 세일이 걸려 있지 않으면 sale_rate가 NULL이라 조건이 참이 되지 않는다.
     */
    private NumberExpression<Long> currentPrice(Instant now) {
        NumberExpression<Long> discounted = Expressions.numberTemplate(Long.class,
                "(({0} * (100 - {1}) / 100) / 10 * 10)", product.minPrice, product.sale.rate);
        return new CaseBuilder().when(saleActive(now)).then(discounted).otherwise(product.minPrice);
    }

    private BooleanExpression saleActive(Instant now) {
        return product.sale.rate.isNotNull()
                .and(product.sale.startsAt.loe(now))
                .and(product.sale.endsAt.gt(now));
    }

    private Predicate[] conditions(ProductSearchCondition condition, List<Long> leafIds,
            Instant now, NumberExpression<Long> currentPrice, BooleanExpression saleActive) {
        List<Predicate> where = new ArrayList<>();
        where.add(product.status.eq(ProductStatus.ON_SALE));
        if (condition.keyword() != null) {
            where.add(Expressions.booleanTemplate("{0} ilike {1} escape '\\'",
                    product.searchText, "%" + escapeLike(condition.keyword()) + "%"));
        }
        if (leafIds != null) {
            where.add(product.categoryId.in(leafIds));
        }
        if (condition.brandIds() != null && !condition.brandIds().isEmpty()) {
            where.add(product.brandId.in(condition.brandIds()));
        }
        if (condition.minPrice() != null) {
            where.add(currentPrice.goe(condition.minPrice()));
        }
        if (condition.maxPrice() != null) {
            where.add(currentPrice.loe(condition.maxPrice()));
        }
        if (condition.onlyOnSale()) {
            where.add(saleActive);
        }
        if (condition.cursor() != null) {
            where.add(afterCursor(condition, currentPrice));
        }
        return where.toArray(Predicate[]::new);
    }

    /** 정렬 순서와 똑같은 기준의 키셋 조건이다. */
    private BooleanExpression afterCursor(ProductSearchCondition condition, NumberExpression<Long> currentPrice) {
        ProductCursor.Decoded cursor = ProductCursor.decode(condition.cursor(), condition.sort());
        return switch (condition.sort()) {
            // created_at <= v 를 따로 두어 (status, created_at, id) 인덱스가 시작 위치를 바로 찾게 한다.
            case NEWEST -> product.createdAt.loe(cursor.instantValue())
                    .and(product.createdAt.lt(cursor.instantValue()).or(product.id.lt(cursor.id())));
            case PRICE_ASC -> currentPrice.gt(cursor.longValue())
                    .or(currentPrice.eq(cursor.longValue()).and(product.id.gt(cursor.id())));
            case PRICE_DESC -> currentPrice.lt(cursor.longValue())
                    .or(currentPrice.eq(cursor.longValue()).and(product.id.lt(cursor.id())));
        };
    }

    private OrderSpecifier<?>[] orderBy(ProductSort sort, NumberExpression<Long> currentPrice) {
        return switch (sort) {
            case NEWEST -> new OrderSpecifier<?>[] {
                    new OrderSpecifier<>(Order.DESC, product.createdAt), new OrderSpecifier<>(Order.DESC, product.id)};
            case PRICE_ASC -> new OrderSpecifier<?>[] {
                    new OrderSpecifier<>(Order.ASC, currentPrice), new OrderSpecifier<>(Order.ASC, product.id)};
            case PRICE_DESC -> new OrderSpecifier<?>[] {
                    new OrderSpecifier<>(Order.DESC, currentPrice), new OrderSpecifier<>(Order.DESC, product.id)};
        };
    }

    /** LIKE 패턴에서 특별한 의미를 갖는 {@code \}, {@code %}, {@code _}를 글자 그대로 찾도록 이스케이프한다. */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
