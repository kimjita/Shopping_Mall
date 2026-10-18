package com.sprint.mission.coupon.repository;

import com.sprint.mission.coupon.domain.Coupon;
import com.sprint.mission.coupon.domain.MemberCoupon;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    /** 회원 쿠폰과 그 쿠폰 정의를 한 번의 조인으로 읽은 한 줄이다. */
    record Row(MemberCoupon memberCoupon, Coupon coupon) {
    }

    /**
     * 이미 받았으면 아무것도 넣지 않고 0을 돌려준다. 동시에 같은 회원이 받으면 하나만 1을 돌려받는다.
     * 무결성 예외를 잡지 않고 {@code on conflict}로 넘기는 것은 트랜잭션이 rollback-only가 되지 않게 하기 위해서다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into member_coupon (member_id, coupon_id, status, created_at, updated_at) "
            + "values (:memberId, :couponId, 'AVAILABLE', :now, :now) "
            + "on conflict (member_id, coupon_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("couponId") Long couponId, @Param("now") Instant now);

    Optional<MemberCoupon> findByMemberIdAndCouponId(Long memberId, Long couponId);

    /** 이 회원의 쿠폰만 찾는다. 남의 쿠폰이면 비어 있다. */
    @Query("select new com.sprint.mission.coupon.repository.MemberCouponRepository$Row(mc, c) "
            + "from MemberCoupon mc join Coupon c on c.id = mc.couponId "
            + "where mc.id = :id and mc.memberId = :memberId")
    Optional<Row> findRowByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    @Query("select new com.sprint.mission.coupon.repository.MemberCouponRepository$Row(mc, c) "
            + "from MemberCoupon mc join Coupon c on c.id = mc.couponId "
            + "where mc.memberId = :memberId order by mc.id desc")
    List<Row> findRowsByMemberId(@Param("memberId") Long memberId);
}
