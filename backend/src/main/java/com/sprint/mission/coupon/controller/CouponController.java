package com.sprint.mission.coupon.controller;

import com.sprint.mission.coupon.dto.DownloadableCouponResponse;
import com.sprint.mission.coupon.dto.MyCouponResponse;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.global.security.CurrentMemberId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/downloadable")
    public List<DownloadableCouponResponse> downloadable(@CurrentMemberId Long memberId) {
        return couponService.downloadable(memberId);
    }

    @PostMapping("/{couponId}/download")
    @ResponseStatus(HttpStatus.CREATED)
    public MyCouponResponse download(@CurrentMemberId Long memberId, @PathVariable Long couponId) {
        return couponService.download(memberId, couponId);
    }

    @GetMapping
    public List<MyCouponResponse> myCoupons(@CurrentMemberId Long memberId) {
        return couponService.myCoupons(memberId);
    }
}
