package com.sprint.mission.order.repository;

import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 이 회원의 주문만 찾는다. 남의 주문이면 비어 있다. */
    Optional<Order> findByOrderNumberAndMemberId(String orderNumber, Long memberId);

    Optional<Order> findByMemberIdAndStatus(Long memberId, OrderStatus status);

    boolean existsByMemberIdAndStatusIn(Long memberId, Collection<OrderStatus> statuses);

    List<Order> findByMemberIdOrderByIdDesc(Long memberId, Limit limit);

    List<Order> findByMemberIdAndIdLessThanOrderByIdDesc(Long memberId, Long id, Limit limit);

    /** 결제 기한이 지난 PENDING 주문의 id를 id 오름차순으로 읽는다. afterId를 넘겨 다음 묶음을 이어 읽는다. */
    @Query("select o.id from Order o where o.status = com.sprint.mission.order.domain.OrderStatus.PENDING "
            + "and o.expiresAt <= :now and o.id > :afterId order by o.id")
    List<Long> findDueIds(@Param("now") Instant now, @Param("afterId") Long afterId, Limit limit);

    /** 잠그기 전에 주문의 회원을 알아내는 데 쓴다. */
    @Query("select o.memberId from Order o where o.id = :id")
    Optional<Long> findMemberIdById(@Param("id") Long id);
}
