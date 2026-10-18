package com.sprint.mission.point.controller;

import com.sprint.mission.point.dto.AdminPointGrantRequest;
import com.sprint.mission.point.dto.PointBalanceResponse;
import com.sprint.mission.point.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members/{memberId}/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointService pointService;

    @PostMapping
    public PointBalanceResponse grant(@PathVariable Long memberId, @Valid @RequestBody AdminPointGrantRequest request) {
        return new PointBalanceResponse(pointService.grant(memberId, request.amount(), request.reason().strip()));
    }
}
