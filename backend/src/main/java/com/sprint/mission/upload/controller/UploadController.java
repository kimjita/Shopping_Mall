package com.sprint.mission.upload.controller;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.upload.dto.UploadRequest;
import com.sprint.mission.upload.dto.UploadResponse;
import com.sprint.mission.upload.service.UploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping
    public UploadResponse presign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest request) {
        return uploadService.presign(Role.valueOf(jwt.getClaimAsString("role")), request);
    }
}
