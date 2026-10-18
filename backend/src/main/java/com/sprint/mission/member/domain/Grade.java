package com.sprint.mission.member.domain;

/** 회원 등급. 선언 순서가 높낮이다(BABY가 가장 낮다). */
public enum Grade {
    BABY, PINK, GREEN, BLACK, GOLD;

    public boolean isAtLeast(Grade other) {
        return compareTo(other) >= 0;
    }
}
