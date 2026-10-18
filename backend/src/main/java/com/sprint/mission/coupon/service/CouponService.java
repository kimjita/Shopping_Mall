package com.sprint.mission.coupon.service;

import com.sprint.mission.coupon.domain.Coupon;
import com.sprint.mission.coupon.domain.MemberCoupon;
import com.sprint.mission.coupon.domain.MemberCouponStatus;
import com.sprint.mission.coupon.dto.DownloadableCouponResponse;
import com.sprint.mission.coupon.dto.MyCouponResponse;
import com.sprint.mission.coupon.repository.CouponRepository;
import com.sprint.mission.coupon.repository.MemberCouponRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.service.MemberService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 선착순 다운로드 쿠폰의 발급과 조회를 다룬다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberService memberService;
    private final Clock clock;

    /**
     * 쿠폰을 받는다. 1인 1장은 {@code insert ... on conflict do nothing}, 선착순은 조건부 UPDATE 한 문장으로 지킨다.
     * 수량이 없어 예외를 던지면 트랜잭션이 롤백되어 앞서 넣은 회원 쿠폰 행도 사라진다.
     */
    @Transactional
    public MyCouponResponse download(Long memberId, Long couponId) {
        Member member = memberService.getActive(memberId);
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        Instant now = clock.instant();
        if (!coupon.isIssuable(now, member.getGrade())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_ISSUABLE);
        }
        if (memberCouponRepository.insertIfAbsent(memberId, couponId, now) == 0) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
        if (couponRepository.increaseIssuedQuantity(couponId, now) == 0) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }
        MemberCoupon memberCoupon = memberCouponRepository.findByMemberIdAndCouponId(memberId, couponId)
                .orElseThrow(() -> new IllegalStateException("방금 넣은 회원 쿠폰을 찾을 수 없습니다."));
        return MyCouponResponse.of(memberCoupon, coupon, now);
    }

    /** 지금 발급 기간이고 수량이 남았고 이 회원이 아직 받지 않은 쿠폰이다. 등급 미달 쿠폰도 eligible=false로 포함한다. */
    public List<DownloadableCouponResponse> downloadable(Long memberId) {
        Member member = memberService.getActive(memberId);
        return couponRepository.findDownloadable(memberId, clock.instant()).stream()
                .map(coupon -> DownloadableCouponResponse.of(coupon, member.getGrade()))
                .toList();
    }

    /** 받은 쿠폰을 최근 순으로 읽는다. 사용 기간이 끝난 AVAILABLE 쿠폰은 EXPIRED로 보인다. */
    public List<MyCouponResponse> myCoupons(Long memberId) {
        memberService.getActive(memberId);
        Instant now = clock.instant();
        return memberCouponRepository.findRowsByMemberId(memberId).stream()
                .map(row -> MyCouponResponse.of(row.memberCoupon(), row.coupon(), now))
                .toList();
    }

    /**
     * 주문이 쓸 쿠폰을 확인한다. 이 회원의 쿠폰이 아니면 {@code COUPON_NOT_FOUND},
     * AVAILABLE이 아니거나 사용 기간 밖이면 {@code COUPON_NOT_USABLE}이다. 회원 확인은 호출한 주문 서비스가 한다.
     */
    public UsableCoupon getUsable(Long memberId, Long memberCouponId, Instant now) {
        MemberCouponRepository.Row row = memberCouponRepository.findRowByIdAndMemberId(memberCouponId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        Coupon coupon = row.coupon();
        if (row.memberCoupon().getStatus() != MemberCouponStatus.AVAILABLE || !coupon.isUsable(now)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_USABLE);
        }
        return new UsableCoupon(row.memberCoupon().getId(), coupon.getId(), coupon.getName(), coupon.terms());
    }
}
