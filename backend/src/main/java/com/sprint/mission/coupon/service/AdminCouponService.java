package com.sprint.mission.coupon.service;

import com.sprint.mission.coupon.domain.Coupon;
import com.sprint.mission.coupon.dto.CouponCreateRequest;
import com.sprint.mission.coupon.dto.CouponResponse;
import com.sprint.mission.coupon.repository.CouponRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        Coupon coupon = Coupon.create(request.name().strip(), request.discountType(), request.discountValue(),
                request.maxDiscount(), request.minOrderAmount(), request.totalQuantity(), request.requiredGrade(),
                request.issueStartsAt(), request.issueEndsAt(), request.useStartsAt(), request.useEndsAt());
        return CouponResponse.from(couponRepository.save(coupon));
    }

    /** 등록한 쿠폰을 최근 순으로 모두 읽는다. */
    public List<CouponResponse> list() {
        return couponRepository.findAllByOrderByIdDesc().stream().map(CouponResponse::from).toList();
    }
}
