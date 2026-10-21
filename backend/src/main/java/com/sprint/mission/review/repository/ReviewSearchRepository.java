package com.sprint.mission.review.repository;

import static com.sprint.mission.order.domain.QOrder.order;
import static com.sprint.mission.order.domain.QOrderItem.orderItem;
import static com.sprint.mission.review.domain.QReview.review;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.review.domain.Review;
import com.sprint.mission.review.domain.ReviewSort;
import com.sprint.mission.review.service.ReviewCursor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 정렬·필터 조합이 많은 리뷰 목록과 작성 가능한 주문상품 조회다. 모두 커서(키셋) 방식이라 인덱스를 따라 limit개만 읽는다. */
@Repository
@RequiredArgsConstructor
public class ReviewSearchRepository {

    private final JPAQueryFactory queryFactory;

    /** 상품의 리뷰를 정렬 순서대로 limit개 읽는다. cursor가 있으면 그 다음부터, skinType이 있으면 스냅샷 피부 타입으로 거른다. */
    public List<Review> findByProduct(Long productId, ReviewSort sort, SkinType skinType, ReviewCursor.Decoded cursor,
            int limit) {
        BooleanBuilder where = new BooleanBuilder(review.productId.eq(productId));
        if (skinType != null) {
            where.and(review.skinProfile.skinType.eq(skinType));
        }
        if (cursor != null) {
            where.and(switch (sort) {
                case LATEST -> review.id.lt(cursor.id());
                case RATING_DESC -> review.rating.lt(cursor.rating())
                        .or(review.rating.eq(cursor.rating()).and(review.id.lt(cursor.id())));
                case RATING_ASC -> review.rating.gt(cursor.rating())
                        .or(review.rating.eq(cursor.rating()).and(review.id.lt(cursor.id())));
            });
        }
        OrderSpecifier<?>[] orderBy = switch (sort) {
            case LATEST -> new OrderSpecifier<?>[] {review.id.desc()};
            case RATING_DESC -> new OrderSpecifier<?>[] {review.rating.desc(), review.id.desc()};
            case RATING_ASC -> new OrderSpecifier<?>[] {review.rating.asc(), review.id.desc()};
        };
        return queryFactory.selectFrom(review).where(where).orderBy(orderBy).limit(limit).fetch();
    }

    /** 내 구매확정 주문의 주문상품 중 리뷰가 없는 것을 주문상품 id 내림차순으로 읽는다. 각 줄은 (주문상품, 주문)이다. */
    public List<Tuple> findWritable(Long memberId, Long cursorId, int limit) {
        BooleanBuilder where = new BooleanBuilder(order.memberId.eq(memberId))
                .and(order.status.eq(OrderStatus.CONFIRMED))
                .and(JPAExpressions.selectOne().from(review).where(review.orderItemId.eq(orderItem.id)).notExists());
        if (cursorId != null) {
            where.and(orderItem.id.lt(cursorId));
        }
        return queryFactory.select(orderItem, order).from(orderItem)
                .join(order).on(order.id.eq(orderItem.orderId))
                .where(where).orderBy(orderItem.id.desc()).limit(limit).fetch();
    }
}
