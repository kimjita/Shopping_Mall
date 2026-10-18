package com.sprint.mission.recentview.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.recentview.dto.RecentViewItem;
import com.sprint.mission.recentview.dto.RecentViewRequest;
import com.sprint.mission.recentview.service.RecentViewService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/recent-views")
@RequiredArgsConstructor
public class RecentViewController {

    private final RecentViewService recentViewService;

    @PostMapping
    public ResponseEntity<Void> record(@CurrentMemberId Long memberId, @Valid @RequestBody RecentViewRequest request) {
        recentViewService.record(memberId, request.productId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<RecentViewItem> list(@CurrentMemberId Long memberId) {
        return recentViewService.list(memberId);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@CurrentMemberId Long memberId, @PathVariable Long productId) {
        recentViewService.remove(memberId, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@CurrentMemberId Long memberId) {
        recentViewService.clear(memberId);
        return ResponseEntity.noContent().build();
    }
}
