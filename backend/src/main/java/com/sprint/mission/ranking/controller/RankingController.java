package com.sprint.mission.ranking.controller;

import com.sprint.mission.ranking.dto.RankingResponse;
import com.sprint.mission.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    public RankingResponse list(@RequestParam(required = false) Long categoryId) {
        return rankingService.top(categoryId);
    }
}
