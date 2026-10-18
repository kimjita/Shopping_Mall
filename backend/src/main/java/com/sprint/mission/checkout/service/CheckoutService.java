package com.sprint.mission.checkout.service;

import com.sprint.mission.cart.domain.CartItemStatus;
import com.sprint.mission.cart.domain.CartLine;
import com.sprint.mission.cart.service.CartLineLoader;
import com.sprint.mission.checkout.domain.PriceCalculator;
import com.sprint.mission.checkout.domain.PriceLine;
import com.sprint.mission.checkout.dto.CheckoutLine;
import com.sprint.mission.checkout.dto.CheckoutPreviewRequest;
import com.sprint.mission.checkout.dto.CheckoutPreviewResponse;
import com.sprint.mission.coupon.domain.CouponTerms;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.coupon.service.UsableCoupon;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.ProductSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문서 금액 미리보기다. 검증과 계산은 {@link #prepare}에 모아 주문 생성이 그대로 다시 쓴다. */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final MemberService memberService;
    private final CartLineLoader cartLineLoader;
    private final CouponService couponService;
    private final PointService pointService;
    private final Clock clock;

    /** 읽기 전용이다. 한 요청은 하나의 now로 세일가와 쿠폰 기간을 판단한다. */
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(Long memberId, CheckoutPreviewRequest request) {
        Checkout checkout = prepare(memberId, request.cartItemIds(), request.memberCouponId(), request.points(),
                Instant.now(clock));
        return new CheckoutPreviewResponse(checkout.lines().stream().map(CheckoutService::toLine).toList(),
                checkout.breakdown(), checkout.pointBalance());
    }

    /**
     * 순서대로 회원 확인, 줄 ID 중복 확인, 줄 읽기, 구매 가능 확인, 쿠폰 확인, 적립금 잔액 확인, 금액 계산을 한다.
     * 자기 트랜잭션을 열지 않는다. 호출자의 트랜잭션(주문 생성은 회원 잠금 뒤의 쓰기 트랜잭션)에 참여하고,
     * 없으면 각 읽기가 따로 실행된다.
     */
    public Checkout prepare(Long memberId, List<Long> cartItemIds, Long memberCouponId, long points, Instant now) {
        memberService.getActive(memberId);
        if (new HashSet<>(cartItemIds).size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<CartLine> lines = cartLineLoader.loadSelected(memberId, cartItemIds, now);
        if (lines.stream().anyMatch(line -> line.status() != CartItemStatus.AVAILABLE)) {
            throw new BusinessException(ErrorCode.CART_ITEM_UNAVAILABLE);
        }
        CouponTerms terms = null;
        if (memberCouponId != null) {
            UsableCoupon coupon = couponService.getUsable(memberId, memberCouponId, now);
            terms = coupon.terms();
        }
        long balance = pointService.balance(memberId);
        if (points > balance) {
            throw new BusinessException(ErrorCode.POINTS_NOT_ENOUGH);
        }
        var breakdown = PriceCalculator.calculate(lines.stream()
                .map(line -> new PriceLine(line.unitPrice(), line.unitSalePrice(), line.item().getQuantity()))
                .toList(), terms, points);
        return new Checkout(lines, memberCouponId, breakdown, balance);
    }

    private static CheckoutLine toLine(CartLine line) {
        ProductSummary summary = line.card().summary();
        return new CheckoutLine(line.item().getId(), line.sku().getId(), summary.name(), line.sku().getName(),
                summary.thumbnailUrl(), line.item().getQuantity(), line.unitPrice(), line.unitSalePrice(),
                line.lineTotal());
    }
}
