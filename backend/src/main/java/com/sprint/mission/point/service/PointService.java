package com.sprint.mission.point.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.point.domain.PointHistory;
import com.sprint.mission.point.domain.PointType;
import com.sprint.mission.point.dto.PointHistoryItem;
import com.sprint.mission.point.dto.PointHistoryPage;
import com.sprint.mission.point.repository.PointHistoryRepository;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립금 잔액과 원장을 다룬다. 잔액은 {@code member.point_balance}를 조건부 UPDATE 한 문장으로만 바꾸고,
 * 같은 트랜잭션에서 원장을 남긴다. 호출한 쪽(주문 등)의 트랜잭션에 합류하므로 주문이 실패하면 함께 되돌아간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    /** 커서는 마지막 내역 id의 10진수 문자열이다. Long 범위를 넘는 값은 파싱에서 걸러진다. */
    private static final Pattern CURSOR = Pattern.compile("[1-9][0-9]{0,18}");

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PointHistoryRepository pointHistoryRepository;
    private final Clock clock;

    /** 구매 적립. 적립 후 잔액을 돌려준다. */
    @Transactional
    public long earn(Long memberId, long amount, String reason, Long orderId) {
        return change(memberId, PointType.EARN, amount, reason, orderId);
    }

    /** 주문 사용. 잔액이 모자라면 {@code POINTS_NOT_ENOUGH}이고 아무것도 바뀌지 않는다. 사용 후 잔액을 돌려준다. */
    @Transactional
    public long use(Long memberId, long amount, String reason, Long orderId) {
        return change(memberId, PointType.USE, amount, reason, orderId);
    }

    /** 주문 취소 등으로 사용한 적립금을 되돌린다. 복원 후 잔액을 돌려준다. */
    @Transactional
    public long restore(Long memberId, long amount, String reason, Long orderId) {
        return change(memberId, PointType.RESTORE, amount, reason, orderId);
    }

    /** 관리자 지급. 금액 범위(1~1,000,000)는 API가 검증한다. 지급 후 잔액을 돌려준다. */
    @Transactional
    public long grant(Long memberId, long amount, String reason) {
        return change(memberId, PointType.ADMIN_GRANT, amount, reason, null);
    }

    public long balance(Long memberId) {
        memberService.getActive(memberId);
        return memberRepository.findPointBalanceById(memberId);
    }

    /** 최근 순으로 읽는다. nextCursor가 null이면 마지막 페이지다. */
    public PointHistoryPage history(Long memberId, String cursor, int size) {
        memberService.getActive(memberId);
        Long cursorId = parseCursor(cursor);

        // 다음 페이지가 있는지 알기 위해 한 개 더 읽는다.
        Limit limit = Limit.of(size + 1);
        List<PointHistory> rows = cursorId == null
                ? pointHistoryRepository.findByMemberIdOrderByIdDesc(memberId, limit)
                : pointHistoryRepository.findByMemberIdAndIdLessThanOrderByIdDesc(memberId, cursorId, limit);
        boolean hasNext = rows.size() > size;
        List<PointHistory> page = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new PointHistoryPage(page.stream().map(PointHistoryItem::from).toList(), nextCursor);
    }

    private long change(Long memberId, PointType type, long amount, String reason, Long orderId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("적립금 변경 금액은 양수여야 합니다.");
        }
        memberService.getActive(memberId);
        long delta = type.isDeduction() ? -amount : amount;
        if (memberRepository.changePointBalance(memberId, delta, clock.instant()) == 0) {
            throw new BusinessException(ErrorCode.POINTS_NOT_ENOUGH);
        }
        // 엔티티는 잔액을 읽기 전용으로만 매핑하고 벌크 UPDATE 뒤 비워지므로, 바뀐 잔액은 DB에서 다시 읽는다.
        long balanceAfter = memberRepository.findPointBalanceById(memberId);
        pointHistoryRepository.save(PointHistory.of(memberId, type, amount, balanceAfter, reason, orderId));
        return balanceAfter;
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        if (!CURSOR.matcher(cursor).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
