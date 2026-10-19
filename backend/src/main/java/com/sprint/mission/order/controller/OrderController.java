package com.sprint.mission.order.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(@CurrentMemberId Long memberId,
            @Valid @RequestBody OrderCreateRequest request) {
        OrderDetailResponse order = orderService.create(memberId, request);
        return ResponseEntity.created(URI.create("/api/members/me/orders/" + order.orderNumber())).body(order);
    }
}
