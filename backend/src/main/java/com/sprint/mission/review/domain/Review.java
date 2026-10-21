package com.sprint.mission.review.domain;

import com.sprint.mission.global.common.BaseEntity;
import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinProfile;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 구매확정된 주문상품에 쓴 리뷰다. 주문상품·회원·상품은 id로만 참조한다.
 * 피부 프로필은 작성 시점 회원 값의 스냅샷이라 수정해도 바꾸지 않는다.
 * 요청 검증은 DTO가 먼저 하고, 여기서는 규칙을 어기면 {@link IllegalArgumentException}으로 마지막 방어선을 친다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MAX_CONTENT_LENGTH = 1_000;
    private static final int MAX_IMAGES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long orderItemId;

    @Column(nullable = false, updatable = false)
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private Long productId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 컬럼이 모두 NULL이면 Hibernate가 null로 읽으므로 null을 "프로필 없음"으로 본다. */
    @Embedded
    private SkinProfile skinProfile;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "review_skin_concern", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "concern", nullable = false, length = 30)
    private Set<SkinConcern> skinConcerns = new HashSet<>();

    /** 첫 번째가 대표 사진이다. */
    @ElementCollection
    @CollectionTable(name = "review_image", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, length = 500)
    private List<String> imageUrls = new ArrayList<>();

    public static Review write(Long orderItemId, Long memberId, Long productId, int rating, String content,
            List<String> imageUrls, SkinProfile skinProfile, Set<SkinConcern> concerns) {
        Review review = new Review();
        review.orderItemId = orderItemId;
        review.memberId = memberId;
        review.productId = productId;
        review.rating = validRating(rating);
        review.content = validContent(content);
        review.imageUrls = new ArrayList<>(validImages(imageUrls));
        review.skinProfile = skinProfile;
        review.skinConcerns = new HashSet<>(concerns);
        return review;
    }

    /** 별점·본문·사진을 바꾸고 별점이 얼마나 달라졌는지(새 별점 - 옛 별점) 돌려준다. 이미지 목록은 통째로 교체한다. */
    public int edit(int rating, String content, List<String> imageUrls) {
        int newRating = validRating(rating);
        String newContent = validContent(content);
        List<String> newImages = validImages(imageUrls);
        int delta = newRating - this.rating;
        this.rating = newRating;
        this.content = newContent;
        this.imageUrls.clear();
        this.imageUrls.addAll(newImages);
        return delta;
    }

    private static int validRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("별점은 1~5여야 한다.");
        }
        return rating;
    }

    private static String validContent(String content) {
        String stripped = content.strip();
        if (stripped.length() < MIN_CONTENT_LENGTH || stripped.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("리뷰 본문은 공백을 뗀 길이가 10~1,000자여야 한다.");
        }
        return stripped;
    }

    private static List<String> validImages(List<String> imageUrls) {
        if (imageUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("리뷰 사진은 5개 이하여야 한다.");
        }
        return imageUrls;
    }
}
