package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.AdminProductResponse;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.SkuUpdateRequest;
import com.sprint.mission.product.dto.StockAdjustRequest;
import com.sprint.mission.product.dto.StockResponse;
import com.sprint.mission.product.repository.SkuRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 옵션(SKU)을 관리하고 재고를 늘리거나 줄인다. 삭제된 상품의 옵션은 건드릴 수 없다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSkuService {

    static final int MAX_SKUS_PER_PRODUCT = 20;

    private final SkuRepository skuRepository;
    private final ProductService productService;
    private final AdminProductService adminProductService;
    private final Clock clock;

    @Transactional
    public AdminProductResponse add(Long productId, SkuRequest request) {
        Product product = productService.getProductForUpdate(productId);
        if (skuRepository.countByProductId(productId) >= MAX_SKUS_PER_PRODUCT) {
            throw new BusinessException(ErrorCode.SKU_LIMIT_EXCEEDED);
        }
        if (skuRepository.existsByProductIdAndName(productId, request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SKU_NAME);
        }
        saveAndFlushOrDuplicate(Sku.create(productId, request.name(), request.additionalPrice(), request.stock()));
        product.refreshMinPrice(skuRepository.findAllByProductIdOrderByIdAsc(productId));
        return adminProductService.get(productId);
    }

    /** 이름과 추가금액만 바꾼다. 같은 이름의 다른 옵션이 있으면 거절하고, 자기 자신의 이름은 허용한다. */
    @Transactional
    public AdminProductResponse update(Long productId, Long skuId, SkuUpdateRequest request) {
        Product product = productService.getProductForUpdate(productId);
        Sku sku = getSku(productId, skuId);
        if (skuRepository.existsByProductIdAndNameAndIdNot(productId, request.name(), skuId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SKU_NAME);
        }
        sku.update(request.name(), request.additionalPrice());
        saveAndFlushOrDuplicate(sku);
        product.refreshMinPrice(skuRepository.findAllByProductIdOrderByIdAsc(productId));
        return adminProductService.get(productId);
    }

    /**
     * 재고를 {@code delta}만큼 바꾼다. 결과가 0 미만이면 STOCK_NOT_ENOUGH, 100,000 초과면 INVALID_INPUT이다.
     * 조건 검사와 갱신을 UPDATE 한 문장에 담아 동시에 호출해도 재고가 음수가 되지 않는다.
     */
    @Transactional
    public StockResponse adjustStock(Long productId, Long skuId, StockAdjustRequest request) {
        productService.getProduct(productId);
        getSku(productId, skuId);
        int updated = skuRepository.adjustStock(skuId, request.delta(), Instant.now(clock));
        if (updated == 0) {
            // 재고는 항상 0~100,000 안에 있고 옵션은 삭제되지 않으므로, 출고 실패는 부족이고 입고 실패는 상한 초과다.
            throw new BusinessException(request.delta() < 0
                    ? ErrorCode.STOCK_NOT_ENOUGH
                    : ErrorCode.INVALID_INPUT);
        }
        // 벌크 UPDATE는 영속성 컨텍스트를 갱신하지 않으므로 바뀐 재고를 스칼라 쿼리로 다시 읽는다.
        int stock = skuRepository.findStockById(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
        return new StockResponse(skuId, stock);
    }

    private Sku getSku(Long productId, Long skuId) {
        return skuRepository.findByIdAndProductId(skuId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    /** 사전 확인과 저장 사이에 같은 이름이 끼어들면 unique 제약이 막는다. 그 경우도 같은 오류로 돌려준다. */
    private void saveAndFlushOrDuplicate(Sku sku) {
        try {
            skuRepository.saveAndFlush(sku);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_SKU_NAME);
        }
    }
}
