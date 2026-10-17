package com.sprint.mission.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.member.AdminProperties;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAccountInitializerTest extends IntegrationTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private AdminAccountInitializer initializer(String email, String password) {
        return new AdminAccountInitializer(new AdminProperties(email, password), memberRepository, passwordEncoder);
    }

    @Test
    void 설정이_있으면_ADMIN_계정을_만든다() {
        boolean created = initializer("Admin@Mall.com", "admin-password").createIfAbsent();

        assertThat(created).isTrue();
        Member admin = memberRepository.findByEmail("admin@mall.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("admin-password", admin.getPassword())).isTrue();
    }

    @Test
    void 이미_있으면_만들지_않는다() {
        AdminAccountInitializer initializer = initializer("admin@mall.com", "admin-password");
        initializer.createIfAbsent();

        assertThat(initializer.createIfAbsent()).isFalse();
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_있는_일반_회원의_역할은_바꾸지_않는다() {
        memberRepository.save(Member.create("admin@mall.com", passwordEncoder.encode("password1"), "홍길동", "01012345678", Role.USER));

        assertThat(initializer("admin@mall.com", "admin-password").createIfAbsent()).isFalse();
        assertThat(memberRepository.findByEmail("admin@mall.com").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void 설정이_비어_있으면_아무것도_하지_않는다() {
        assertThat(initializer("", "").createIfAbsent()).isFalse();
        assertThat(initializer(null, "admin-password").createIfAbsent()).isFalse();
        assertThat(initializer("admin@mall.com", "  ").createIfAbsent()).isFalse();
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    void 너무_긴_비밀번호는_만들지_않는다() {
        assertThat(initializer("admin@mall.com", "a1".repeat(40)).createIfAbsent()).isFalse();
        assertThat(memberRepository.count()).isZero();
    }
}
