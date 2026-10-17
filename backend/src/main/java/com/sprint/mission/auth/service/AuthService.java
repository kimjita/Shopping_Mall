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
import java.time.Instant;
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

    /** 없는 회원에게도 똑같이 비교하려고 쓰는 해시다. 처음 쓸 때 만든다. */
    private volatile String dummyPasswordHash;

    /**
     * 회원이 없거나 탈퇴했거나 비밀번호가 틀리면 모두 같은 INVALID_CREDENTIALS다.
     * 계정 존재 여부를 응답 내용으로도, 응답 시간으로도 알 수 없게 회원이 없을 때도 비밀번호 비교를 한 번 한다.
     * BCrypt 비교 동안 DB 연결을 잡지 않으려고 트랜잭션을 걸지 않는다. 조회·저장은 저장소가 각각 짧게 처리한다.
     */
    public IssuedTokens login(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(EmailNormalizer.normalize(email)).orElse(null);
        if (member == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(rawPassword, member.getPassword()) || !member.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(member, UUID.randomUUID());
    }

    /**
     * 한 번 쓴 refresh token을 다시 쓰면 탈취로 보고 그 패밀리를 모두 폐기한다.
     * 동시에 같은 토큰으로 오면 markUsed의 행 잠금 덕분에 하나만 이기고, 진 쪽이 패밀리를 폐기한다.
     * 폐기가 예외와 함께 롤백되면 안 돼서 BusinessException에는 롤백하지 않는다.
     * BusinessException은 쓰기 전에 나거나 의도한 쓰기 뒤에만 난다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(refreshToken))
                .filter(token -> !token.isExpired(now) && !token.isRevoked())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshTokenRepository.markUsed(stored.getId(), now) == 0) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(stored.getMemberId())
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issueTokens(member, stored.getFamilyId());
    }

    /** 없거나 잘못된 토큰이어도 예외 없이 끝난다. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(refreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private IssuedTokens issueTokens(Member member, UUID familyId) {
        String refreshToken = refreshTokenGenerator.generate();
        refreshTokenRepository.save(RefreshToken.issue(
                member.getId(),
                familyId,
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
