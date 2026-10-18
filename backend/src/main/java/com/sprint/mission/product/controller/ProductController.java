package com.sprint.mission.product.controller;

import com.sprint.mission.product.dto.ProductPage;
import com.sprint.mission.product.dto.ProductResponse;
import com.sprint.mission.product.dto.ProductSearchCondition;
import com.sprint.mission.product.service.ProductSearchService;
import com.sprint.mission.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;

    @GetMapping
    public ProductPage list(@Valid @ModelAttribute ProductSearchCondition condition) {
        return productSearchService.search(condition);
    }

    @GetMapping("/{productId}")
    public ProductResponse get(@PathVariable Long productId) {
        return productService.getPublic(productId);
    }
}
