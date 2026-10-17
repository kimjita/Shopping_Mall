package com.sprint.mission.auth.service;

import com.sprint.mission.auth.RefreshTokenProperties;
import com.sprint.mission.auth.domain.RefreshToken;
import com.sprint.mission.auth.dto.IssuedTokens;
import com.sprint.mission.auth.repository.RefreshTokenRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.global.security.JwtTokenProvider;
import com.sprint.mission.member.domain.EmailNormalizer;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.repository.MemberRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenProperties refreshTokenProperties;
    private final Clock clock;

    /** 없는 회원에게도 똑같이 비교하려고 쓰는 해시예요. 처음 쓸 때 만들어요. */
    private volatile String dummyPasswordHash;

    /**
     * 회원이 없거나 탈퇴했거나 비밀번호가 틀리면 모두 같은 INVALID_CREDENTIALS예요.
     * 계정 존재 여부를 응답 내용으로도, 응답 시간으로도 알 수 없게 회원이 없을 때도 비밀번호 비교를 한 번 해요.
     */
    @Transactional
    public IssuedTokens login(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(EmailNormalizer.normalize(email)).orElse(null);
        if (member == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(rawPassword, member.getPassword()) || !member.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String refreshToken = refreshTokenGenerator.generate();
        refreshTokenRepository.save(RefreshToken.issue(
                member.getId(),
                UUID.randomUUID(),
                RefreshTokenGenerator.hash(refreshToken),
                clock.instant().plus(refreshTokenProperties.ttl())));

        return new IssuedTokens(
                jwtTokenProvider.createAccessToken(member.getId(), member.getRole()),
                jwtTokenProvider.accessTokenTtlSeconds(),
                refreshToken);
    }

    private String dummyPasswordHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode("dummy-password");
        }
        return dummyPasswordHash;
    }
}
