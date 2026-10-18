package com.sprint.mission.wishlist.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.wishlist.dto.WishedProductIds;
import com.sprint.mission.wishlist.dto.WishlistPage;
import com.sprint.mission.wishlist.service.WishlistService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PutMapping("/{productId}")
    public ResponseEntity<Void> add(@CurrentMemberId Long memberId, @PathVariable Long productId) {
        wishlistService.add(memberId, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@CurrentMemberId Long memberId, @PathVariable Long productId) {
        wishlistService.remove(memberId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public WishlistPage list(@CurrentMemberId Long memberId,
            @RequestParam(required = false) @Size(max = 20, message = "커서가 올바르지 않습니다.") String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이어야 합니다.")
            @Max(value = 50, message = "size는 1~50이어야 합니다.") int size) {
        return wishlistService.list(memberId, cursor, size);
    }

    @GetMapping("/ids")
    public WishedProductIds wished(@CurrentMemberId Long memberId,
            @RequestParam
            @Size(min = 1, max = 100, message = "상품 ID는 1~100개여야 합니다.")
            List<@NotNull(message = "상품 ID가 올바르지 않습니다.") Long> productIds) {
        return wishlistService.wished(memberId, productIds);
    }
}
