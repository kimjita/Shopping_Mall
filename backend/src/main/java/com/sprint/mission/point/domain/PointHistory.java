package com.sprint.mission.point.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 적립금 원장의 한 줄이다. 한 번 쓰면 바꾸지 않는다. balanceAfter는 그 변경 직후의 회원 잔액이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointType type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 100)
    private String reason;

    private Long orderId;

    public static PointHistory of(Long memberId, PointType type, long amount, long balanceAfter, String reason,
            Long orderId) {
        PointHistory history = new PointHistory();
        history.memberId = memberId;
        history.type = type;
        history.amount = amount;
        history.balanceAfter = balanceAfter;
        history.reason = reason;
        history.orderId = orderId;
        return history;
    }
}
