package com.sprint.mission.review.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.review.domain.ReviewSort;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 상품 리뷰 목록의 커서다. 정렬 종류, 마지막 항목의 별점, id를 {@code SORT|값|id}로 이어 Base64url(패딩 없음)로 인코딩한다.
 * 별점은 별점 정렬일 때만 1~5로 담고 LATEST면 비운다. 해석할 수 없거나 정렬이 다르면 INVALID_INPUT이다.
 */
public final class ReviewCursor {

    private static final String DELIMITER = "|";

    private ReviewCursor() {
    }

    /** rating은 LATEST면 무시한다. */
    public static String encode(ReviewSort sort, int rating, Long id) {
        String raw = sort.name() + DELIMITER + (sort == ReviewSort.LATEST ? "" : rating) + DELIMITER + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor, ReviewSort expected) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + DELIMITER, -1);
            if (parts.length != 3 || ReviewSort.valueOf(parts[0]) != expected) {
                throw invalid();
            }
            int rating = expected == ReviewSort.LATEST ? 0 : Integer.parseInt(parts[1]);
            long id = Long.parseLong(parts[2]);
            boolean validValue = expected == ReviewSort.LATEST ? parts[1].isEmpty() : rating >= 1 && rating <= 5;
            if (id < 1 || !validValue) {
                throw invalid();
            }
            return new Decoded(rating, id);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }

    /** rating은 LATEST면 0이다. */
    public record Decoded(int rating, long id) {
    }
}
