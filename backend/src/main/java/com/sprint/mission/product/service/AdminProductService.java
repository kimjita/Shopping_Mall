package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.AdminProductResponse;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.product.repository.SkuRepository;
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

    /** 삭제된 상품은 관리자도 볼 수 없다. */
    public AdminProductResponse get(Long productId) {
        Product product = productService.getProduct(productId);
        List<String> categoryPath = categoryService.path(product.getCategoryId()).stream()
                .map(Category::getName)
                .toList();
        return AdminProductResponse.of(product, brandService.get(product.getBrandId()).getName(), categoryPath,
                skuRepository.findAllByProductIdOrderByIdAsc(productId));
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
