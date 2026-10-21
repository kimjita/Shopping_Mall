package com.sprint.mission.review.service;

import com.querydsl.core.Tuple;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.product.service.ProductService;
import com.sprint.mission.review.domain.Review;
import com.sprint.mission.review.domain.ReviewSort;
import com.sprint.mission.review.dto.MyReviewPage;
import com.sprint.mission.review.dto.MyReviewResponse;
import com.sprint.mission.review.dto.ProductReviewPage;
import com.sprint.mission.review.dto.ProductReviewResponse;
import com.sprint.mission.review.dto.WritableReviewItem;
import com.sprint.mission.review.dto.WritableReviewPage;
import com.sprint.mission.review.repository.ReviewRepository;
import com.sprint.mission.review.repository.ReviewSearchRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 목록을 읽는다. 한 페이지의 작성자·주문상품은 id 묶음으로 한 번에 읽고, 사진·피부 고민은 배치 페치로 읽으므로
 * 쿼리 수가 페이지 크기와 무관하다. 모든 목록은 다음 페이지가 있는지 알기 위해 한 개 더 읽는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewQueryService {

    private static final Pattern DECIMAL_CURSOR = Pattern.compile("[1-9][0-9]{0,18}");
    private static final String WITHDRAWN_NAME = "탈퇴한 회원";

    private final ProductService productService;
    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewSearchRepository reviewSearchRepository;

    /** 없거나 DELETED인 상품은 PRODUCT_NOT_FOUND다. 판매 중지된 상품의 리뷰는 읽을 수 있다. */
    public ProductReviewPage productReviews(Long productId, ReviewSort sort, SkinType skinType, String cursor,
            int size) {
        productService.getProduct(productId);
        ReviewCursor.Decoded decoded = cursor == null || cursor.isEmpty() ? null : ReviewCursor.decode(cursor, sort);

        List<Review> rows = reviewSearchRepository.findByProduct(productId, sort, skinType, decoded, size + 1);
        boolean hasNext = rows.size() > size;
        List<Review> page = hasNext ? rows.subList(0, size) : rows;

        Map<Long, Member> authors = memberRepository.findAllById(
                page.stream().map(Review::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, OrderItem> items = orderItemsOf(page);
        List<ProductReviewResponse> responses = page.stream()
                .map(r -> ProductReviewResponse.of(r, authorName(authors.get(r.getMemberId())),
                        items.get(r.getOrderItemId()).getSkuName()))
                .toList();
        Review last = page.isEmpty() ? null : page.get(page.size() - 1);
        return new ProductReviewPage(responses, hasNext ? ReviewCursor.encode(sort, last.getRating(), last.getId()) : null);
    }

    public MyReviewPage myReviews(Long memberId, String cursor, int size) {
        memberService.getActive(memberId);
        Long cursorId = parseDecimalCursor(cursor);

        List<Review> rows = cursorId == null
                ? reviewRepository.findByMemberIdOrderByIdDesc(memberId, Limit.of(size + 1))
                : reviewRepository.findByMemberIdAndIdLessThanOrderByIdDesc(memberId, cursorId, Limit.of(size + 1));
        boolean hasNext = rows.size() > size;
        List<Review> page = hasNext ? rows.subList(0, size) : rows;

        Map<Long, OrderItem> items = orderItemsOf(page);
        return new MyReviewPage(page.stream().map(r -> MyReviewResponse.of(r, items.get(r.getOrderItemId()))).toList(),
                hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null);
    }

    public WritableReviewPage writable(Long memberId, String cursor, int size) {
        memberService.getActive(memberId);
        List<Tuple> rows = reviewSearchRepository.findWritable(memberId, parseDecimalCursor(cursor), size + 1);
        boolean hasNext = rows.size() > size;
        List<Tuple> page = hasNext ? rows.subList(0, size) : rows;

        List<WritableReviewItem> items = page.stream()
                .map(t -> WritableReviewItem.of(t.get(0, OrderItem.class), t.get(1, Order.class))).toList();
        return new WritableReviewPage(items, hasNext ? String.valueOf(items.get(items.size() - 1).orderItemId()) : null);
    }

    private Map<Long, OrderItem> orderItemsOf(List<Review> reviews) {
        return orderItemRepository.findAllById(reviews.stream().map(Review::getOrderItemId).toList())
                .stream().collect(Collectors.toMap(OrderItem::getId, Function.identity()));
    }

    /** 첫 글자만 보이고 나머지는 가린다. 탈퇴한 회원은 이름을 쓰지 않는다. */
    private static String authorName(Member author) {
        if (!author.isActive()) {
            return WITHDRAWN_NAME;
        }
        return new String(Character.toChars(author.getName().codePointAt(0))) + "**";
    }

    /** {@code PointService.history}와 같은 규칙이다. 비어 있으면 첫 페이지이고, 1 이상의 10진수가 아니면 INVALID_INPUT이다. */
    private static Long parseDecimalCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        if (!DECIMAL_CURSOR.matcher(cursor).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
