package com.sprint.mission.review.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.order.repository.OrderRepository;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.review.domain.Review;
import com.sprint.mission.review.dto.MyReviewResponse;
import com.sprint.mission.review.dto.ReviewEditRequest;
import com.sprint.mission.review.dto.ReviewWriteRequest;
import com.sprint.mission.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰를 쓰고 고치고 지운다. 모든 요청이 회원 행을 먼저 잠그므로 같은 회원의 요청은 줄을 서고(같은 주문상품에 동시에 두 번 써도 하나만 저장된다),
 * 상품 평점은 같은 트랜잭션에서 {@link ProductRepository#changeRating} 한 문장으로 바꾼다(잠금 순서: 회원 → 상품).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MAX_CONTENT_LENGTH = 1_000;

    private final MemberService memberService;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * 오류는 다음 순서로 처음 걸린 것을 돌려준다: 입력 규칙(400 INVALID_INPUT, 본문 길이 포함)
     * → 회원(404 MEMBER_NOT_FOUND) → 내 주문상품(404 ORDER_ITEM_NOT_FOUND, 없는 것과 남의 것이 같다) → 구매확정(409 REVIEW_NOT_WRITABLE)
     * → 중복(409 REVIEW_ALREADY_EXISTS).
     */
    @Transactional
    public MyReviewResponse write(Long memberId, ReviewWriteRequest request) {
        checkContentLength(request.content());
        Member member = memberService.getActiveForUpdate(memberId);
        OrderItem item = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND));
        Order order = orderRepository.findById(item.getOrderId())
                .filter(o -> o.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND));
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_WRITABLE);
        }
        if (reviewRepository.existsByOrderItemId(item.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        Review review = reviewRepository.save(Review.write(item.getId(), memberId, item.getProductId(),
                request.rating(), request.content(), request.imageUrls(), member.getSkinProfile(),
                member.getSkinConcerns()));
        productRepository.changeRating(item.getProductId(), 1, review.getRating());
        return MyReviewResponse.of(review, item);
    }

    /** 오류 순서: 입력 규칙(400, 본문 길이 포함) → 회원 → 내 리뷰(404 REVIEW_NOT_FOUND, 없는 것과 남의 것이 같다). 피부 스냅샷은 바꾸지 않는다. */
    @Transactional
    public MyReviewResponse edit(Long memberId, Long reviewId, ReviewEditRequest request) {
        checkContentLength(request.content());
        memberService.getActiveForUpdate(memberId);
        Review review = reviewRepository.findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        int diff = review.edit(request.rating(), request.content(), request.imageUrls());
        if (diff != 0) {
            productRepository.changeRating(review.getProductId(), 0, diff);
        }
        reviewRepository.flush(); // 별점이 같으면 쓰기 쿼리가 없어서, 응답에 담을 updatedAt을 여기서 확정한다.
        return MyReviewResponse.of(review, orderItemRepository.findById(review.getOrderItemId()).orElseThrow());
    }

    @Transactional
    public void delete(Long memberId, Long reviewId) {
        memberService.getActiveForUpdate(memberId);
        Review review = reviewRepository.findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        reviewRepository.delete(review);
        productRepository.changeRating(review.getProductId(), -1, -review.getRating());
    }

    /** 공백만 있는 본문과 지나치게 큰 본문은 DTO가 막고, 공백을 뗀 길이 10~1,000자는 여기서 확인한다. */
    private static void checkContentLength(String content) {
        int length = content.strip().length();
        if (length < MIN_CONTENT_LENGTH || length > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "리뷰 내용은 10~1,000자여야 합니다.");
        }
    }
}
