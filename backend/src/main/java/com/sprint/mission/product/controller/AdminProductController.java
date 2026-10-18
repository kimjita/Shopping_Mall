package com.sprint.mission.product.controller;

import com.sprint.mission.product.dto.AdminProductResponse;
import com.sprint.mission.product.dto.IdResponse;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.ProductUpdateRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.service.AdminProductService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/{productId}")
    public AdminProductResponse update(@PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        return adminProductService.update(productId, request);
    }

    @PatchMapping("/{productId}/status")
    public AdminProductResponse changeStatus(@PathVariable Long productId,
            @Valid @RequestBody ProductStatusRequest request) {
        return adminProductService.changeStatus(productId, request);
    }

    @PutMapping("/{productId}/sale")
    public AdminProductResponse startSale(@PathVariable Long productId, @Valid @RequestBody SaleRequest request) {
        return adminProductService.startSale(productId, request);
    }

    @DeleteMapping("/{productId}/sale")
    public ResponseEntity<Void> endSale(@PathVariable Long productId) {
        adminProductService.endSale(productId);
        return ResponseEntity.noContent().build();
    }
}
