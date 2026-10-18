package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sale;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.AdminProductResponse;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.ProductUpdateRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.product.repository.SkuRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductService productService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final Clock clock;

    @Transactional
    public Long create(ProductCreateRequest request) {
        brandService.get(request.brandId());
        categoryService.getLeaf(request.categoryId());
        validateUniqueSkuNames(request.skus());

        Product product = productRepository.save(Product.create(
                request.brandId(), request.categoryId(), request.name(), request.summary(),
                request.description(), request.ingredients(), request.basePrice(), request.imageUrls()));
        skuRepository.saveAll(request.skus().stream()
                .map(sku -> Sku.create(product.getId(), sku.name(), sku.additionalPrice(), sku.stock()))
                .toList());
        return product.getId();
    }

    /** 상품 정보를 바꾼다. 브랜드와 카테고리는 등록할 때와 똑같이 확인한다. 삭제된 상품은 수정할 수 없다. */
    @Transactional
    public AdminProductResponse update(Long productId, ProductUpdateRequest request) {
        Product product = productService.getProduct(productId);
        brandService.get(request.brandId());
        categoryService.getLeaf(request.categoryId());
        product.update(request.brandId(), request.categoryId(), request.name(), request.summary(),
                request.description(), request.ingredients(), request.basePrice(), request.imageUrls());
        return toResponse(product);
    }

    /** 삭제된 상품은 상태를 되돌릴 수 없다. */
    @Transactional
    public AdminProductResponse changeStatus(Long productId, ProductStatusRequest request) {
        Product product = productService.getProduct(productId);
        product.changeStatus(request.status());
        return toResponse(product);
    }

    /** 세일을 건다. 기존 세일은 교체되고, 종료 시각이 시작 시각보다 늦지 않으면 INVALID_SALE_PERIOD다. */
    @Transactional
    public AdminProductResponse startSale(Long productId, SaleRequest request) {
        Product product = productService.getProduct(productId);
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException(ErrorCode.INVALID_SALE_PERIOD);
        }
        product.startSale(new Sale(request.rate(), request.startsAt(), request.endsAt()));
        return toResponse(product);
    }

    /** 세일이 없어도 성공한다. */
    @Transactional
    public void endSale(Long productId) {
        productService.getProduct(productId).endSale();
    }

    /** 삭제된 상품은 관리자도 볼 수 없다. */
    public AdminProductResponse get(Long productId) {
        return toResponse(productService.getProduct(productId));
    }

    /** 방금 DELETED로 바꾼 상품의 응답도 만들어야 해서 상태를 거르지 않는다. */
    private AdminProductResponse toResponse(Product product) {
        List<String> categoryPath = categoryService.path(product.getCategoryId()).stream()
                .map(Category::getName)
                .toList();
        return AdminProductResponse.of(product, brandService.get(product.getBrandId()).getName(), categoryPath,
                skuRepository.findAllByProductIdOrderByIdAsc(product.getId()), Instant.now(clock));
    }

    private static void validateUniqueSkuNames(List<SkuRequest> skus) {
        Set<String> names = new HashSet<>();
        for (SkuRequest sku : skus) {
            if (!names.add(sku.name())) {
                throw new BusinessException(ErrorCode.DUPLICATE_SKU_NAME);
            }
        }
    }
}
