package com.sprint.mission.product.dto;

/** 상품 목록 정렬이다. 동률은 항상 id로 끊는다. */
public enum ProductSort {
    /** 등록 시각 내림차순, 동률이면 id 내림차순이다. */
    NEWEST,
    /** 현재 판매가 오름차순, 동률이면 id 오름차순이다. */
    PRICE_ASC,
    /** 현재 판매가 내림차순, 동률이면 id 내림차순이다. */
    PRICE_DESC,
    /** 평균 평점 내림차순, 동률이면 id 내림차순이다. 리뷰가 없는 상품은 맨 뒤에 id 내림차순으로 온다. */
    RATING,
    /** 리뷰 수 내림차순, 동률이면 id 내림차순이다. */
    REVIEW_COUNT
}
