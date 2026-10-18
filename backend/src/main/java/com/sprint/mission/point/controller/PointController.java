package com.sprint.mission.point.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.point.dto.PointBalanceResponse;
import com.sprint.mission.point.dto.PointHistoryPage;
import com.sprint.mission.point.service.PointService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping
    public PointBalanceResponse balance(@CurrentMemberId Long memberId) {
        return new PointBalanceResponse(pointService.balance(memberId));
    }

    @GetMapping("/history")
    public PointHistoryPage history(@CurrentMemberId Long memberId,
            @RequestParam(required = false) @Size(max = 20, message = "커서가 올바르지 않습니다.") String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이어야 합니다.")
            @Max(value = 50, message = "size는 1~50이어야 합니다.") int size) {
        return pointService.history(memberId, cursor, size);
    }
}
