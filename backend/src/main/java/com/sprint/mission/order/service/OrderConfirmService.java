package com.sprint.mission.order.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.repository.OrderItemRepository;
import com.sprint.mission.order.repository.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderConfirmService {

    /** 배송 완료 후 이 기간이 지나면 자동으로 구매확정한다. */
    static final Duration AUTO_CONFIRM_AFTER = Duration.ofDays(7);

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderConfirmer orderConfirmer;
    private final Clock clock;

    /**
     * 내 배송 완료 주문을 구매확정하고 적립한다. 회원 행을 먼저 잠그므로 같은 주문의 자동 확정과 줄을 서고,
     * 이미 확정된 주문은 적립 없이 그대로 돌려준다.
     */
    @Transactional
    public OrderDetailResponse confirmByMember(Long memberId, String orderNumber) {
        Member member = memberService.getActiveForUpdate(memberId);
        Order order = orderRepository.findByOrderNumberAndMemberId(orderNumber, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new BusinessException(ErrorCode.ORDER_NOT_CONFIRMABLE);
            }
            orderConfirmer.confirm(order, member, clock.instant());
        }
        return OrderDetailResponse.of(order, orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()));
    }

    /**
     * 배송 완료 후 7일이 지난 주문 하나를 구매확정하고 적립한다. 확정했으면 true다.
     * 만료 취소와 같이 회원 행을 먼저 잠그고(탈퇴 여부와 상관없이) 그다음에 주문을 읽어, 그사이 사용자가 확정한 주문을 다시 처리하지 않는다.
     */
    @Transactional
    public boolean autoConfirm(Long orderId, Instant now) {
        Long memberId = orderRepository.findMemberIdById(orderId).orElse(null);
        if (memberId == null) {
            return false;
        }
        Member member = memberRepository.findWithLockById(memberId).orElseThrow();
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.DELIVERED
                || order.getDeliveredAt().plus(AUTO_CONFIRM_AFTER).isAfter(now)) {
            return false;
        }
        orderConfirmer.confirm(order, member, now);
        return true;
    }
}
