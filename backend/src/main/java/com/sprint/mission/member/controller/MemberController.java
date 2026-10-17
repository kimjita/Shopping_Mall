package com.sprint.mission.member.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.member.dto.ChangePasswordRequest;
import com.sprint.mission.member.dto.MemberResponse;
import com.sprint.mission.member.dto.UpdateProfileRequest;
import com.sprint.mission.member.dto.UpdateSkinProfileRequest;
import com.sprint.mission.member.dto.WithdrawRequest;
import com.sprint.mission.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public MemberResponse me(@CurrentMemberId Long memberId) {
        return memberService.getMe(memberId);
    }

    @PatchMapping
    public MemberResponse updateProfile(@CurrentMemberId Long memberId, @Valid @RequestBody UpdateProfileRequest request) {
        return memberService.updateProfile(memberId, request.name(), request.phone());
    }

    @PutMapping("/skin-profile")
    public MemberResponse updateSkinProfile(@CurrentMemberId Long memberId, @RequestBody UpdateSkinProfileRequest request) {
        return memberService.updateSkinProfile(memberId, request.skinType(), request.skinTone(), request.skinConcerns());
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@CurrentMemberId Long memberId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        memberService.changePassword(memberId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> withdraw(@CurrentMemberId Long memberId, @Valid @RequestBody WithdrawRequest request) {
        memberService.withdraw(memberId, request.password());
        return ResponseEntity.noContent().build();
    }
}
