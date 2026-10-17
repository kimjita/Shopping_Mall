package com.sprint.mission.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 생성하면_ACTIVE_USER다() {
        Member member = Member.create("a@mall.com", "encoded", "홍길동", "01012345678", Role.USER);

        assertThat(member.getEmail()).isEqualTo("a@mall.com");
        assertThat(member.getPassword()).isEqualTo("encoded");
        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getPhone()).isEqualTo("01012345678");
        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.isActive()).isTrue();
        assertThat(member.getSkinConcerns()).isEmpty();
    }
}
