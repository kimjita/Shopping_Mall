package com.sprint.mission.recommendation.controller;

import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.recommendation.dto.RecommendationResponse;
import com.sprint.mission.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** skinType은 필수다. 없거나 SkinType 이름이 아니면 400이다. */
    @GetMapping
    public RecommendationResponse list(@RequestParam SkinType skinType) {
        return recommendationService.get(skinType);
    }
}
