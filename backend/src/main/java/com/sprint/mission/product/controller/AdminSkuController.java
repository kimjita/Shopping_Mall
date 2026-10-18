package com.sprint.mission.product.controller;

import com.sprint.mission.product.dto.AdminProductResponse;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.SkuUpdateRequest;
import com.sprint.mission.product.dto.StockAdjustRequest;
import com.sprint.mission.product.dto.StockResponse;
import com.sprint.mission.product.service.AdminSkuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products/{productId}/skus")
@RequiredArgsConstructor
public class AdminSkuController {

    private final AdminSkuService adminSkuService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse add(@PathVariable Long productId, @Valid @RequestBody SkuRequest request) {
        return adminSkuService.add(productId, request);
    }

    @PutMapping("/{skuId}")
    public AdminProductResponse update(@PathVariable Long productId, @PathVariable Long skuId,
            @Valid @RequestBody SkuUpdateRequest request) {
        return adminSkuService.update(productId, skuId, request);
    }

    @PostMapping("/{skuId}/stock-adjustments")
    public StockResponse adjustStock(@PathVariable Long productId, @PathVariable Long skuId,
            @Valid @RequestBody StockAdjustRequest request) {
        return adminSkuService.adjustStock(productId, skuId, request);
    }
}
