package com.sprint.mission.recentview.dto;

import jakarta.validation.constraints.NotNull;

public record RecentViewRequest(@NotNull(message = "상품 ID는 필수입니다.") Long productId) {
}
