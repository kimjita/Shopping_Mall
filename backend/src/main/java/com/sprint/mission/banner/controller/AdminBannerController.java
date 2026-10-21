package com.sprint.mission.banner.controller;

import com.sprint.mission.banner.dto.BannerRequest;
import com.sprint.mission.banner.dto.BannerResponse;
import com.sprint.mission.banner.service.BannerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @PostMapping
    public ResponseEntity<BannerResponse> create(@Valid @RequestBody BannerRequest request) {
        BannerResponse response = bannerService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/banners/" + response.id())).body(response);
    }

    @PutMapping("/{bannerId}")
    public BannerResponse update(@PathVariable Long bannerId, @Valid @RequestBody BannerRequest request) {
        return bannerService.update(bannerId, request);
    }

    @DeleteMapping("/{bannerId}")
    public ResponseEntity<Void> delete(@PathVariable Long bannerId) {
        bannerService.delete(bannerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<BannerResponse> list() {
        return bannerService.listAll();
    }
}
