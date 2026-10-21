package com.sprint.mission.banner.controller;

import com.sprint.mission.banner.dto.BannerResponse;
import com.sprint.mission.banner.service.BannerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping
    public List<BannerResponse> list() {
        return bannerService.listActive();
    }
}
