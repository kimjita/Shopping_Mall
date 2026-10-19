package com.sprint.mission.order.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.dto.OrderPage;
import com.sprint.mission.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public OrderPage list(@CurrentMemberId Long memberId,
            @RequestParam(required = false) @Size(max = 20, message = "커서가 올바르지 않습니다.") String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이어야 합니다.")
            @Max(value = 50, message = "size는 1~50이어야 합니다.") int size) {
        return orderService.list(memberId, cursor, size);
    }

    @GetMapping("/{orderNumber}")
    public OrderDetailResponse get(@CurrentMemberId Long memberId, @PathVariable String orderNumber) {
        return orderService.get(memberId, orderNumber);
    }
}
