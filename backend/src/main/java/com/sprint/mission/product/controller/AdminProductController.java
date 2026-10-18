package com.sprint.mission.product.controller;

import com.sprint.mission.product.dto.AdminProductResponse;
import com.sprint.mission.product.dto.IdResponse;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.service.AdminProductService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    @PostMapping
    public ResponseEntity<IdResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        Long id = adminProductService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/products/" + id)).body(new IdResponse(id));
    }

    @GetMapping("/{productId}")
    public AdminProductResponse get(@PathVariable Long productId) {
        return adminProductService.get(productId);
    }
}
