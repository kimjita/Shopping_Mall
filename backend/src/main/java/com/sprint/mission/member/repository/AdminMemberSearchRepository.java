package com.sprint.mission.member.repository;

import static com.sprint.mission.member.domain.QMember.member;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.dto.AdminMemberSearchCondition;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 관리자 회원 목록이다. 탈퇴 회원도 포함해 id 내림차순(최근 가입 순)으로 읽는다. */
@Repository
@RequiredArgsConstructor
public class AdminMemberSearchRepository {

    private final JPAQueryFactory queryFactory;

    /** cursorId보다 작은 id의 회원을 limit명까지 읽는다. cursorId가 null이면 처음부터 읽는다. */
    public List<Member> search(AdminMemberSearchCondition condition, Long cursorId, int limit) {
        List<Predicate> where = new ArrayList<>();
        if (condition.keyword() != null) {
            String pattern = "%" + escapeLike(condition.keyword()) + "%";
            where.add(Expressions.booleanTemplate("{0} ilike {1} escape '\\'", member.email, pattern)
                    .or(Expressions.booleanTemplate("{0} ilike {1} escape '\\'", member.name, pattern)));
        }
        if (cursorId != null) {
            where.add(member.id.lt(cursorId));
        }
        return queryFactory.selectFrom(member)
                .where(where.toArray(Predicate[]::new))
                .orderBy(member.id.desc())
                .limit(limit)
                .fetch();
    }

    /** LIKE 패턴에서 특별한 의미를 갖는 {@code \}, {@code %}, {@code _}를 글자 그대로 찾도록 이스케이프한다. */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
