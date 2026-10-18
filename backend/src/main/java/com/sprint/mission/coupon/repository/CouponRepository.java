package com.sprint.mission.coupon.repository;

import com.sprint.mission.coupon.domain.Coupon;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    List<Coupon> findAllByOrderByIdDesc();

    /**
     * 선착순 발급 수량을 하나 늘리는 한 문장이다. 남은 수량이 없으면 바꾸지 않고 0을 돌려준다.
     * 읽기 전용 매핑 컬럼이라 JPQL로는 갱신할 수 없어 네이티브 쿼리를 쓴다.
     * 호출한 트랜잭션이 읽어 둔 다른 엔티티가 떨어져 나가지 않도록 영속성 컨텍스트는 비우지 않는다.
     * 그래서 이 트랜잭션 안의 {@code Coupon.issuedQuantity}는 낡은 값일 수 있다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "update coupon set issued_quantity = issued_quantity + 1, updated_at = :now "
            + "where id = :id and issued_quantity < total_quantity", nativeQuery = true)
    int increaseIssuedQuantity(@Param("id") Long id, @Param("now") Instant now);

    /** 발급 기간 안이고 수량이 남았고 이 회원이 아직 받지 않은 쿠폰이다. 등급 조건은 걸지 않는다. */
    @Query("select c from Coupon c "
            + "where c.issueStartsAt <= :now and c.issueEndsAt > :now and c.issuedQuantity < c.totalQuantity "
            + "and not exists (select 1 from MemberCoupon mc where mc.couponId = c.id and mc.memberId = :memberId) "
            + "order by c.id desc")
    List<Coupon> findDownloadable(@Param("memberId") Long memberId, @Param("now") Instant now);
}
