package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.dto.ProductResponse;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.product.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자·공개 서비스가 함께 쓰는 상품 조회다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final BrandService brandService;
    private final CategoryService categoryService;

    /** 없거나 DELETED면 PRODUCT_NOT_FOUND다. */
    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 고객에게 보이는 상품 상세다. ON_SALE이 아니면 PRODUCT_NOT_FOUND다. */
    public ProductResponse getPublic(Long id) {
        Product product = getProduct(id);
        if (!product.isVisibleToPublic()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return ProductResponse.of(product, brandService.get(product.getBrandId()),
                categoryService.path(product.getCategoryId()),
                skuRepository.findAllByProductIdOrderByIdAsc(id));
    }
}
