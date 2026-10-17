package com.sprint.mission.member.service;

import com.sprint.mission.member.AdminProperties;
import com.sprint.mission.member.domain.EmailNormalizer;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 환경변수(ADMIN_EMAIL, ADMIN_PASSWORD)가 있으면 시작할 때 관리자 계정을 한 번 만들어요.
 */
@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final AdminProperties properties;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(
            AdminProperties properties, MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent();
    }

    /**
     * @return 새로 만들었으면 true. 설정이 비었거나 이미 있는 이메일이거나 비밀번호가 너무 길면 false예요.
     */
    public boolean createIfAbsent() {
        String rawEmail = properties.email();
        String password = properties.password();
        if (rawEmail == null || rawEmail.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            log.warn("초기 관리자 비밀번호가 {}바이트를 넘어 계정을 만들지 않았어요.", BCRYPT_MAX_BYTES);
            return false;
        }
        String email = EmailNormalizer.normalize(rawEmail);
        if (memberRepository.existsByEmail(email)) {
            return false;
        }
        memberRepository.save(Member.create(email, passwordEncoder.encode(password), "관리자", "01000000000", Role.ADMIN));
        log.info("초기 관리자 계정을 생성했습니다: {}", email);
        return true;
    }
}
