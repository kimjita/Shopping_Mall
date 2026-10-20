package com.sprint.mission.order.controller;

import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.dto.AdminOrderDetailResponse;
import com.sprint.mission.order.dto.AdminOrderPage;
import com.sprint.mission.order.service.AdminOrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    public AdminOrderPage list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @Size(max = 20, message = "커서가 올바르지 않습니다.") String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이어야 합니다.")
            @Max(value = 50, message = "size는 1~50이어야 합니다.") int size) {
        return adminOrderService.list(status, cursor, size);
    }

    @GetMapping("/{orderNumber}")
    public AdminOrderDetailResponse get(@PathVariable String orderNumber) {
        return adminOrderService.get(orderNumber);
    }
}
