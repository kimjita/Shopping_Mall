package com.sprint.mission.checkout.controller;

import com.sprint.mission.checkout.dto.CheckoutPreviewRequest;
import com.sprint.mission.checkout.dto.CheckoutPreviewResponse;
import com.sprint.mission.checkout.service.CheckoutService;
import com.sprint.mission.global.security.CurrentMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    /** 주문을 만들지 않고 금액만 계산한다. 값을 바꾸지 않으므로 POST여도 200이다. */
    @PostMapping("/preview")
    public CheckoutPreviewResponse preview(@CurrentMemberId Long memberId,
            @Valid @RequestBody CheckoutPreviewRequest request) {
        return checkoutService.preview(memberId, request);
    }
}
