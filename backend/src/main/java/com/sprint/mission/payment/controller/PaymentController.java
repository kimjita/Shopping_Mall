package com.sprint.mission.payment.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.payment.dto.PaymentConfirmRequest;
import com.sprint.mission.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/orders/{orderNumber}")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payment")
    public OrderDetailResponse confirm(@CurrentMemberId Long memberId, @PathVariable String orderNumber,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return paymentService.confirm(memberId, orderNumber, request);
    }

    @PostMapping("/cancel")
    public OrderDetailResponse cancel(@CurrentMemberId Long memberId, @PathVariable String orderNumber) {
        return paymentService.cancel(memberId, orderNumber);
    }
}
