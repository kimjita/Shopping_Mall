package com.sprint.mission.member.dto;

import com.sprint.mission.member.domain.Grade;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinProfile;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import java.util.List;

public record MemberResponse(
        Long id,
        String email,
        String name,
        String phone,
        Role role,
        Grade grade,
        SkinType skinType,
        SkinTone skinTone,
        List<SkinConcern> skinConcerns) {

    /** 피부 프로필이 비어 있으면 Hibernate가 null로 읽어오므로 null도 처리한다. 고민은 enum 선언 순서로 정렬한다. */
    public static MemberResponse from(Member member) {
        SkinProfile skinProfile = member.getSkinProfile();
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getPhone(),
                member.getRole(),
                member.getGrade(),
                skinProfile == null ? null : skinProfile.getSkinType(),
                skinProfile == null ? null : skinProfile.getSkinTone(),
                member.getSkinConcerns().stream().sorted().toList());
    }
}
