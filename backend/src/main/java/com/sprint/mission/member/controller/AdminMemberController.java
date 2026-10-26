package com.sprint.mission.member.controller;

import com.sprint.mission.member.dto.AdminMemberDetail;
import com.sprint.mission.member.dto.AdminMemberPage;
import com.sprint.mission.member.dto.AdminMemberSearchCondition;
import com.sprint.mission.member.service.AdminMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public AdminMemberPage list(@Valid @ModelAttribute AdminMemberSearchCondition condition) {
        return adminMemberService.list(condition);
    }

    @GetMapping("/{memberId}")
    public AdminMemberDetail get(@PathVariable Long memberId) {
        return adminMemberService.get(memberId);
    }
}
