package com.sprint.mission.review.domain;

/** 리뷰 목록 정렬 기준이다. 같은 별점은 최신(id 내림차순)이 먼저다. */
public enum ReviewSort {
    LATEST,
    RATING_DESC,
    RATING_ASC
}
