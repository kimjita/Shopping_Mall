package com.sprint.mission.order.service;

import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.order.domain.CancelReason;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.repository.OrderRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final OrderCanceler orderCanceler;

    /**
     * 주문 하나를 만료 취소하고 재고·쿠폰·적립금을 되돌린다. 취소했으면 true다.
     * 같은 회원의 새 주문과 줄을 서도록 회원 행을 먼저 잠그고(탈퇴 여부와 상관없이), 그다음에 주문을 읽는다.
     * 잠그기 전에 주문 엔티티를 읽으면 그사이 새 주문이 대체한 낡은 상태를 보게 되므로, 회원 id만 스칼라로 읽는다.
     */
    @Transactional
    public boolean expire(Long orderId, Instant now) {
        Long memberId = orderRepository.findMemberIdById(orderId).orElse(null);
        if (memberId == null) {
            return false;
        }
        memberRepository.findWithLockById(memberId);
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING || !order.isExpired(now)) {
            return false;
        }
        orderCanceler.cancel(order, CancelReason.EXPIRED, now);
        return true;
    }
}
