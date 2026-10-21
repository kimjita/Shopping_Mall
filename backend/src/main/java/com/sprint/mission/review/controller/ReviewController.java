package com.sprint.mission.review.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.review.dto.MyReviewPage;
import com.sprint.mission.review.dto.MyReviewResponse;
import com.sprint.mission.review.dto.ReviewEditRequest;
import com.sprint.mission.review.dto.ReviewWriteRequest;
import com.sprint.mission.review.dto.WritableReviewPage;
import com.sprint.mission.review.service.ReviewQueryService;
import com.sprint.mission.review.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewQueryService reviewQueryService;

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

    @GetMapping
    public MyReviewPage myReviews(@CurrentMemberId Long memberId,
            @RequestParam(required = false) @Size(max = 20, message = "커서가 올바르지 않습니다.") String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이어야 합니다.")
            @Max(value = 50, message = "size는 1~50이어야 합니다.") int size) {
        return reviewQueryService.myReviews(memberId, cursor, size);
    }

    @GetMapping("/writable")
    public WritableReviewPage writable(@CurrentMemberId Long memberId,
            @RequestParam(required = false) @Size(max = 20, message = "커서가 올바르지 않습니다.") String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이어야 합니다.")
            @Max(value = 50, message = "size는 1~50이어야 합니다.") int size) {
        return reviewQueryService.writable(memberId, cursor, size);
    }
}
