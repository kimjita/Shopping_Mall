package com.sprint.mission.cart.controller;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.dto.CartItemResponse;
import com.sprint.mission.cart.dto.CartQuantityRequest;
import com.sprint.mission.cart.dto.CartResponse;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.global.security.CurrentMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse get(@CurrentMemberId Long memberId) {
        return cartService.get(memberId);
    }

    /** 이미 담은 옵션을 합쳐도 201이고, 합쳐진 줄을 돌려준다. */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse add(@CurrentMemberId Long memberId, @Valid @RequestBody CartAddRequest request) {
        return cartService.add(memberId, request);
    }

    @PatchMapping("/items/{cartItemId}")
    public CartItemResponse changeQuantity(@CurrentMemberId Long memberId, @PathVariable Long cartItemId,
            @Valid @RequestBody CartQuantityRequest request) {
        return cartService.changeQuantity(memberId, cartItemId, request.quantity());
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> remove(@CurrentMemberId Long memberId, @PathVariable Long cartItemId) {
        cartService.remove(memberId, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
