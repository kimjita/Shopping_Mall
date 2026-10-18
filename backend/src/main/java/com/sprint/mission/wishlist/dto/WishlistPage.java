package com.sprint.mission.wishlist.dto;

import java.util.List;

/** 커서 방식의 찜 목록 한 페이지다. 마지막 페이지면 nextCursor가 null이다. */
public record WishlistPage(List<WishlistItem> items, String nextCursor) {
}
