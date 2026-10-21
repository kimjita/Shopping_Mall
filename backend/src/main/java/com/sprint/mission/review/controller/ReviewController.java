package com.sprint.mission.review.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.review.dto.MyReviewResponse;
import com.sprint.mission.review.dto.ReviewEditRequest;
import com.sprint.mission.review.dto.ReviewWriteRequest;
import com.sprint.mission.review.service.ReviewService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<MyReviewResponse> write(@CurrentMemberId Long memberId,
            @Valid @RequestBody ReviewWriteRequest request) {
        MyReviewResponse review = reviewService.write(memberId, request);
        return ResponseEntity.created(URI.create("/api/members/me/reviews/" + review.reviewId())).body(review);
    }

    @PutMapping("/{reviewId}")
    public MyReviewResponse edit(@CurrentMemberId Long memberId, @PathVariable Long reviewId,
            @Valid @RequestBody ReviewEditRequest request) {
        return reviewService.edit(memberId, reviewId, request);
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@CurrentMemberId Long memberId, @PathVariable Long reviewId) {
        reviewService.delete(memberId, reviewId);
        return ResponseEntity.noContent().build();
    }
}
