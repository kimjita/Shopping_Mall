package com.sprint.mission.order.service;

import com.sprint.mission.member.domain.GradePolicy;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.point.service.PointService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배송 완료 주문을 구매확정하고 확정 시점의 회원 등급 적립률로 적립한다. 사용자 확정과 자동 확정이 함께 쓴다.
 * 호출한 쪽이 회원 행 잠금과 쓰기 트랜잭션을 가지고 있어야 한다. 그래야 같은 주문이 두 번 적립되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OrderConfirmer {

    private final PointService pointService;
    private final GradePolicy gradePolicy;

    public void confirm(Order order, Member member, Instant now) {
        order.confirm(now);
        pointService.earn(member.getId(), gradePolicy.earnPoints(member.getGrade(), order.earnBase()),
                "구매확정 적립", order.getId());
    }
}
