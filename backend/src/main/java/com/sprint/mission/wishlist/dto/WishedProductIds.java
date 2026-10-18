package com.sprint.mission.wishlist.dto;

import java.util.List;

/** 요청한 상품 중 회원이 찜한 상품 id다. 요청한 순서를 유지한다. */
public record WishedProductIds(List<Long> productIds) {
}
