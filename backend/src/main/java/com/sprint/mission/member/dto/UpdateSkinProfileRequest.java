package com.sprint.mission.member.dto;

import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 모두 선택 항목이에요. 비우면 그 항목을 지워요. */
public record UpdateSkinProfileRequest(SkinType skinType, SkinTone skinTone, List<@NotNull(message = "피부 고민에 null을 넣을 수 없습니다.") SkinConcern> skinConcerns) {
}
