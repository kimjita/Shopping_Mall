package com.sprint.mission.member.service;

import com.sprint.mission.member.domain.Grade;
import com.sprint.mission.member.domain.GradePolicy;
import com.sprint.mission.member.repository.GradeBatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ACTIVE 회원의 등급을 직전 3개 달력 월(서울 시각)의 구매확정 금액으로 다시 매긴다.
 * 등급 규칙은 {@link GradePolicy#gradeFor}에만 있다. 1,000명 묶음마다 트랜잭션을 나눠 긴 배치가 회원 행을 오래 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GradeRecalculationService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int CHUNK_SIZE = 1_000;

    private final GradeBatchRepository gradeBatchRepository;
    private final GradePolicy gradePolicy;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** runMonth 1일 00:00(서울) 실행 기준이다. 등급이 바뀐 회원 수를 돌려준다. */
    public int recalculate(YearMonth runMonth) {
        Instant from = runMonth.minusMonths(3).atDay(1).atStartOfDay(SEOUL).toInstant();
        Instant to = runMonth.atDay(1).atStartOfDay(SEOUL).toInstant();
        Instant now = Instant.now(clock);
        int changed = 0;
        long afterId = 0L;
        while (true) {
            List<Long> ids = gradeBatchRepository.findActiveMemberIds(afterId, CHUNK_SIZE);
            if (ids.isEmpty()) {
                return changed;
            }
            changed += transactionTemplate.execute(status -> updateChunk(ids, from, to, now));
            afterId = ids.get(ids.size() - 1);
        }
    }

    private int updateChunk(List<Long> ids, Instant from, Instant to, Instant now) {
        Map<Long, Long> amounts = gradeBatchRepository.sumConfirmedAmounts(ids, from, to);
        Map<Long, Grade> grades = new TreeMap<>();
        for (Long id : ids) {
            grades.put(id, gradePolicy.gradeFor(amounts.getOrDefault(id, 0L)));
        }
        return gradeBatchRepository.updateGrades(grades, now);
    }
}
