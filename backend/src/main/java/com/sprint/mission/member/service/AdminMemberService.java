package com.sprint.mission.member.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.dto.AdminMemberDetail;
import com.sprint.mission.member.dto.AdminMemberPage;
import com.sprint.mission.member.dto.AdminMemberSearchCondition;
import com.sprint.mission.member.dto.AdminMemberSummary;
import com.sprint.mission.member.repository.AdminMemberSearchRepository;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.order.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private final AdminMemberSearchRepository adminMemberSearchRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;

    /** 탈퇴 회원을 포함해 최근 가입한 회원부터 한 페이지 읽는다. 커서는 마지막 회원의 id다. */
    public AdminMemberPage list(AdminMemberSearchCondition condition) {
        int size = condition.size();
        // 다음 페이지가 있는지 알기 위해 한 개 더 읽는다.
        List<Member> rows = adminMemberSearchRepository.search(condition, condition.cursorId(), size + 1);
        boolean hasNext = rows.size() > size;
        List<Member> page = hasNext ? rows.subList(0, size) : rows;
        return new AdminMemberPage(page.stream().map(AdminMemberSummary::from).toList(),
                hasNext ? String.valueOf(page.get(size - 1).getId()) : null);
    }

    /** 탈퇴 회원도 읽는다. 없는 id만 MEMBER_NOT_FOUND다. */
    public AdminMemberDetail get(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return AdminMemberDetail.of(member, orderRepository.countByMemberId(memberId));
    }
}
