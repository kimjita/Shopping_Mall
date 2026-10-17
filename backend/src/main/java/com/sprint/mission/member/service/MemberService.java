package com.sprint.mission.member.service;

import com.sprint.mission.auth.repository.RefreshTokenRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.EmailNormalizer;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.PhonePolicy;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.member.dto.MemberResponse;
import com.sprint.mission.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public Long signup(String email, String rawPassword, String name, String phone) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        Member member = Member.create(
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                name,
                PhonePolicy.normalize(phone),
                Role.USER);
        try {
            return memberRepository.saveAndFlush(member).getId();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional(readOnly = true)
    public Member getActive(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        return MemberResponse.from(getActive(memberId));
    }

    @Transactional
    public MemberResponse updateProfile(Long memberId, String name, String phone) {
        Member member = getActive(memberId);
        member.updateProfile(name, PhonePolicy.normalize(phone));
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateSkinProfile(Long memberId, SkinType type, SkinTone tone, List<SkinConcern> concerns) {
        Member member = getActive(memberId);
        member.updateSkinProfile(type, tone, concerns == null || concerns.isEmpty() ? null : EnumSet.copyOf(concerns));
        return MemberResponse.from(member);
    }

    /** 바꾸면 이 회원의 모든 refresh token을 폐기해서 다른 기기의 로그인도 끊어요. */
    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = getActiveWithPassword(memberId, currentPassword);
        member.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByMemberId(memberId, clock.instant());
    }

    @Transactional
    public void withdraw(Long memberId, String password) {
        Member member = getActiveWithPassword(memberId, password);
        Instant now = clock.instant();
        member.withdraw(now);
        refreshTokenRepository.revokeAllByMemberId(memberId, now);
    }

    private Member getActiveWithPassword(Long memberId, String rawPassword) {
        Member member = getActive(memberId);
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        return member;
    }
}
