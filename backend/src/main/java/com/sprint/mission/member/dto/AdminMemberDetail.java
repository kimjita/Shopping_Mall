package com.sprint.mission.member.dto;

import com.sprint.mission.member.domain.Grade;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.MemberStatus;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinProfile;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import java.time.Instant;
import java.util.List;

/** 관리자 회원 상세다. orderCount는 취소된 주문을 포함한 모든 주문의 수다. */
public record AdminMemberDetail(Long id, String email, String name, String phone, Role role, Grade grade,
        MemberStatus status, SkinType skinType, SkinTone skinTone, List<SkinConcern> skinConcerns,
        long pointBalance, long orderCount, Instant createdAt, Instant withdrawnAt) {

    /** 피부 프로필이 비어 있으면 Hibernate가 null로 읽어오므로 null도 처리한다. 고민은 enum 선언 순서로 정렬한다. */
    public static AdminMemberDetail of(Member member, long orderCount) {
        SkinProfile skinProfile = member.getSkinProfile();
        return new AdminMemberDetail(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
                member.getRole(), member.getGrade(), member.getStatus(),
                skinProfile == null ? null : skinProfile.getSkinType(),
                skinProfile == null ? null : skinProfile.getSkinTone(),
                member.getSkinConcerns().stream().sorted().toList(),
                member.getPointBalance(), orderCount, member.getCreatedAt(), member.getWithdrawnAt());
    }
}
