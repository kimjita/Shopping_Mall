package com.sprint.mission.coupon.controller;

import com.sprint.mission.coupon.dto.CouponCreateRequest;
import com.sprint.mission.coupon.dto.CouponResponse;
import com.sprint.mission.coupon.service.AdminCouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CouponCreateRequest request) {
        return adminCouponService.create(request);
    }

    @GetMapping
    public List<CouponResponse> list() {
        return adminCouponService.list();
    }
}
