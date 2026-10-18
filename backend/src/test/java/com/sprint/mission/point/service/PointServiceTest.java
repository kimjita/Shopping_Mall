package com.sprint.mission.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.point.domain.PointType;
import com.sprint.mission.point.dto.PointHistoryItem;
import com.sprint.mission.point.dto.PointHistoryPage;
import com.sprint.mission.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PointServiceTest extends IntegrationTest {

    @Autowired
    PointService pointService;

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private long storedBalance() {
        return jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId);
    }

    private int historyRows() {
        return jdbcTemplate.queryForObject("select count(*) from point_history", Integer.class);
    }

    @Test
    void 지급하면_잔액이_늘고_원장이_남는다() {
        long balance = pointService.grant(memberId, 1_000, "이벤트 지급");

        assertThat(balance).isEqualTo(1_000);
        assertThat(storedBalance()).isEqualTo(1_000);
        assertThat(pointService.balance(memberId)).isEqualTo(1_000);
        PointHistoryItem item = pointService.history(memberId, null, 20).items().get(0);
        assertThat(item.type()).isEqualTo(PointType.ADMIN_GRANT);
        assertThat(item.amount()).isEqualTo(1_000);
        assertThat(item.balanceAfter()).isEqualTo(1_000);
        assertThat(item.reason()).isEqualTo("이벤트 지급");
        assertThat(item.orderId()).isNull();
    }

    @Test
    void 사용하면_줄고_복원하면_돌아온다() {
        pointService.earn(memberId, 2_000, "구매 적립", 7L);

        assertThat(pointService.use(memberId, 1_500, "주문 사용", 8L)).isEqualTo(500);
        assertThat(storedBalance()).isEqualTo(500);
        assertThat(pointService.restore(memberId, 1_500, "주문 취소 복원", 8L)).isEqualTo(2_000);
        assertThat(storedBalance()).isEqualTo(2_000);

        List<PointHistoryItem> items = pointService.history(memberId, null, 20).items();
        assertThat(items.get(0).type()).isEqualTo(PointType.RESTORE);
        assertThat(items.get(0).orderId()).isEqualTo(8L);
        assertThat(items.get(1).type()).isEqualTo(PointType.USE);
        assertThat(items.get(2).type()).isEqualTo(PointType.EARN);
    }

    @Test
    void 잔액보다_많이_쓰면_POINTS_NOT_ENOUGH이고_잔액은_그대로다() {
        pointService.grant(memberId, 1_000, "지급");

        assertThatThrownBy(() -> pointService.use(memberId, 1_001, "주문 사용", 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POINTS_NOT_ENOUGH));

        assertThat(storedBalance()).isEqualTo(1_000);
        assertThat(historyRows()).isEqualTo(1);
    }

    @Test
    void 원장의_balance_after가_순서대로_맞다() {
        pointService.grant(memberId, 3_000, "지급");
        pointService.use(memberId, 1_200, "사용", 1L);
        pointService.earn(memberId, 400, "적립", 2L);
        pointService.restore(memberId, 100, "복원", 1L);

        List<PointHistoryItem> items = pointService.history(memberId, null, 20).items();
        assertThat(items).extracting(PointHistoryItem::balanceAfter).containsExactly(2_300L, 2_200L, 1_800L, 3_000L);
        assertThat(storedBalance()).isEqualTo(2_300);
    }

    @Test
    void 내역은_id_내림차순_커서로_이어_읽는다() {
        for (int i = 1; i <= 5; i++) {
            pointService.grant(memberId, 100, "지급 " + i);
        }

        PointHistoryPage first = pointService.history(memberId, null, 2);
        assertThat(first.items()).extracting(PointHistoryItem::reason).containsExactly("지급 5", "지급 4");
        assertThat(first.nextCursor()).isNotNull();

        PointHistoryPage second = pointService.history(memberId, first.nextCursor(), 2);
        assertThat(second.items()).extracting(PointHistoryItem::reason).containsExactly("지급 3", "지급 2");

        PointHistoryPage third = pointService.history(memberId, second.nextCursor(), 2);
        assertThat(third.items()).extracting(PointHistoryItem::reason).containsExactly("지급 1");
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void 호출한_트랜잭션에서_읽은_엔티티의_변경이_유실되지_않는다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member member = memberRepository.findById(memberId).orElseThrow();
            pointService.grant(memberId, 700, "지급");
            pointService.use(memberId, 200, "사용", 1L);
            // 적립금 변경이 영속성 컨텍스트를 비우면 이 변경은 커밋되지 않는다.
            member.updateProfile("새이름", "01099998888");
        });

        assertThat(jdbcTemplate.queryForObject("select name from member where id = ?", String.class, memberId))
                .isEqualTo("새이름");
        assertThat(storedBalance()).isEqualTo(500);
    }

    @Test
    void 해석할_수_없는_커서는_INVALID_INPUT이다() {
        assertThatThrownBy(() -> pointService.history(memberId, "abc", 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 금액이_0_이하면_IllegalArgumentException이다() {
        assertThatThrownBy(() -> pointService.grant(memberId, 0, "지급"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pointService.use(memberId, -1, "사용", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(historyRows()).isZero();
    }

    @Test
    void 없거나_탈퇴한_회원이면_MEMBER_NOT_FOUND이다() {
        assertThatThrownBy(() -> pointService.grant(999_999L, 100, "지급"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));

        memberService.withdraw(memberId, "password1");
        assertThatThrownBy(() -> pointService.grant(memberId, 100, "지급"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> pointService.balance(memberId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
