package com.sprint.mission.member.domain;

/** 휴대폰 번호 규칙이다. 하이픈은 허용하고, 저장할 때는 숫자만 남긴다. */
public final class PhonePolicy {

    public static final String REGEX = "^01[016789]-?\\d{3,4}-?\\d{4}$";
    public static final String MESSAGE = "올바른 휴대폰 번호 형식이 아닙니다.";

    private PhonePolicy() {
    }

    public static String normalize(String phone) {
        return phone.replaceAll("\\D", "");
    }
}
