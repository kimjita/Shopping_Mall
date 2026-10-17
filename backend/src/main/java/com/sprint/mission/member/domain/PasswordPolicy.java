package com.sprint.mission.member.domain;

/**
 * 비밀번호 규칙이다. 공백 없는 ASCII 출력 문자만 허용해서 최대 64바이트가 되고, BCrypt의 72바이트 한도를 넘지 않는다.
 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$";
    public static final String MESSAGE = "비밀번호는 공백 없이 영문·숫자·특수문자 8~64자이며 영문과 숫자를 모두 포함해야 합니다.";

    private PasswordPolicy() {
    }
}
