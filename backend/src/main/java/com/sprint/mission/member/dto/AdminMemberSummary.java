package com.sprint.mission.member.dto;

import com.sprint.mission.member.domain.Grade;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.MemberStatus;
import com.sprint.mission.member.domain.Role;
import java.time.Instant;

/** 관리자 회원 목록의 한 줄이다. 탈퇴 회원이면 withdrawnAt이 있다. */
public record AdminMemberSummary(Long id, String email, String name, Role role, Grade grade, MemberStatus status,
        Instant createdAt, Instant withdrawnAt) {

    public static AdminMemberSummary from(Member member) {
        return new AdminMemberSummary(member.getId(), member.getEmail(), member.getName(), member.getRole(),
                member.getGrade(), member.getStatus(), member.getCreatedAt(), member.getWithdrawnAt());
    }
}
