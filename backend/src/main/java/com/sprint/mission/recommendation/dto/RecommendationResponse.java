package com.sprint.mission.recommendation.dto;

import com.sprint.mission.member.domain.SkinType;
import java.util.List;

public record RecommendationResponse(SkinType skinType, List<RecommendationItem> items) {
}
