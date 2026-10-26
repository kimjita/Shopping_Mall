package com.sprint.mission.member.dto;

import java.util.List;

/** 관리자 회원 한 페이지다. 마지막 페이지면 nextCursor가 null이다. */
public record AdminMemberPage(List<AdminMemberSummary> items, String nextCursor) {
}
