package com.sprint.mission.member.service;

import com.sprint.mission.auth.repository.RefreshTokenRepository;
import com.sprint.mission.cart.repository.CartItemRepository;
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
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.repository.OrderRepository;
import com.sprint.mission.recentview.repository.RecentViewRepository;
import com.sprint.mission.wishlist.repository.WishlistRepository;
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
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistRepository wishlistRepository;
    private final RecentViewRepository recentViewRepository;
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

    /** 회원 행을 잠그고 조회한다. 호출한 트랜잭션이 끝날 때까지 같은 회원의 쓰기가 줄을 선다. */
    @Transactional
    public Member getActiveForUpdate(Long memberId) {
        return memberRepository.findWithLockById(memberId)
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

    /** 바꾸면 이 회원의 모든 refresh token을 폐기해서 다른 기기의 로그인도 끊는다. */
    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = getActiveWithPassword(memberId, currentPassword);
        member.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByMemberId(memberId, clock.instant());
    }

    /**
     * 진행 중인 주문이 있으면 거부한다. 회원 행을 먼저 잠가 같은 회원의 주문 생성과 줄을 세운다.
     * 탈퇴하면 장바구니·찜·최근 본 상품을 지운다. 주문·적립금 기록과 작성한 리뷰는 남긴다.
     */
    @Transactional
    public void withdraw(Long memberId, String password) {
        Member member = getActiveForUpdate(memberId);
        checkPassword(member, password);
        if (orderRepository.existsByMemberIdAndStatusIn(memberId, OrderStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.MEMBER_HAS_ACTIVE_ORDER);
        }
        cartItemRepository.deleteByMemberId(memberId);
        wishlistRepository.deleteAllByMemberId(memberId);
        recentViewRepository.deleteAll(memberId);
        Instant now = clock.instant();
        member.withdraw(now);
        refreshTokenRepository.revokeAllByMemberId(memberId, now);
    }

    private Member getActiveWithPassword(Long memberId, String rawPassword) {
        Member member = getActive(memberId);
        checkPassword(member, rawPassword);
        return member;
    }

    private void checkPassword(Member member, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }
}
