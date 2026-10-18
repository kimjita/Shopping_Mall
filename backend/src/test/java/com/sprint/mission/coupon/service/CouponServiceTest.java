package com.sprint.mission.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.coupon.domain.CouponTerms;
import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Grade;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CouponServiceTest extends IntegrationTest {

    @Autowired
    CouponService couponService;

    @Autowired
    AdminCouponService adminCouponService;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private Long memberCouponIdAfterDownload(Long couponId) {
        return couponService.download(memberId, couponId).id();
    }

    @Test
    void 소진된_쿠폰은_COUPON_SOLD_OUT이고_받은_행이_남지_않는다() {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("한 장", 1, null)).id();
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");
        couponService.download(other, couponId);

        assertThatThrownBy(() -> couponService.download(memberId, couponId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_SOLD_OUT));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from member_coupon where member_id = ?", Integer.class, memberId)).isZero();
        assertThat(couponService.downloadable(memberId)).isEmpty();
    }

    @Test
    void 이미_받은_쿠폰은_COUPON_ALREADY_ISSUED이고_수량이_늘지_않는다() {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("쿠폰", 5, null)).id();
        couponService.download(memberId, couponId);

        assertThatThrownBy(() -> couponService.download(memberId, couponId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_ALREADY_ISSUED));

        assertThat(jdbcTemplate.queryForObject(
                "select issued_quantity from coupon where id = ?", Integer.class, couponId)).isEqualTo(1);
    }

    @Test
    void 없는_쿠폰은_COUPON_NOT_FOUND() {
        assertThatThrownBy(() -> couponService.download(memberId, 999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND));
    }

    @Test
    void 사용할_수_있는_쿠폰은_계산기_입력을_돌려준다() {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("사용 쿠폰", 5, null)).id();
        Long memberCouponId = memberCouponIdAfterDownload(couponId);

        UsableCoupon usable = couponService.getUsable(memberId, memberCouponId, Instant.now());

        assertThat(usable.memberCouponId()).isEqualTo(memberCouponId);
        assertThat(usable.couponId()).isEqualTo(couponId);
        assertThat(usable.name()).isEqualTo("사용 쿠폰");
        assertThat(usable.terms()).isEqualTo(new CouponTerms(DiscountType.FIXED, 3_000, null, 10_000));
    }

    @Test
    void 남의_쿠폰이거나_없는_쿠폰은_COUPON_NOT_FOUND() {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("쿠폰", 5, null)).id();
        Long memberCouponId = memberCouponIdAfterDownload(couponId);
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");

        assertThatThrownBy(() -> couponService.getUsable(other, memberCouponId, Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND));
        assertThatThrownBy(() -> couponService.getUsable(memberId, 999_999L, Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND));
    }

    @Test
    void 사용한_쿠폰과_사용_기간_밖의_쿠폰은_COUPON_NOT_USABLE() {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("쿠폰", 5, null)).id();
        Long memberCouponId = memberCouponIdAfterDownload(couponId);
        Instant useEndsAt = jdbcTemplate.queryForObject(
                "select use_ends_at from coupon where id = ?", java.sql.Timestamp.class, couponId).toInstant();

        // 사용 종료 시각 정각부터는 쓸 수 없다.
        assertNotUsable(memberCouponId, useEndsAt);
        // 사용 기간 시작 전이다.
        assertNotUsable(memberCouponId, Instant.now().minus(Duration.ofDays(10)));

        jdbcTemplate.update("update member_coupon set status = 'USED' where id = ?", memberCouponId);
        assertNotUsable(memberCouponId, Instant.now());
    }

    @Test
    void 등급_전용_쿠폰은_등급이_오르면_받을_수_있다() {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("PINK 전용", 5, Grade.PINK)).id();
        assertThatThrownBy(() -> couponService.download(memberId, couponId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_ISSUABLE));

        jdbcTemplate.update("update member set grade = 'GREEN' where id = ?", memberId);

        assertThat(couponService.download(memberId, couponId).couponId()).isEqualTo(couponId);
    }

    private void assertNotUsable(Long memberCouponId, Instant now) {
        assertThatThrownBy(() -> couponService.getUsable(memberId, memberCouponId, now))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_USABLE));
    }
}
