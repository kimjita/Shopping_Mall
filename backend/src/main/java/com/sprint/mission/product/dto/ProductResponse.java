package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.Brand;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.domain.Sale;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 고객에게 보여 주는 상품 상세다. 재고 수량은 내려주지 않고 품절 여부만 알린다. ratingAvg는 리뷰가 없으면 null이다. */
public record ProductResponse(
        Long id,
        String name,
        BrandSummary brand,
        List<CategorySummary> categoryPath,
        String summary,
        String description,
        String ingredients,
        List<String> imageUrls,
        long basePrice,
        Integer saleRate,
        boolean soldOut,
        List<PublicSku> skus,
        BigDecimal ratingAvg,
        int reviewCount) {

    public record BrandSummary(Long id, String name) {
    }

    public record CategorySummary(Long id, String name) {
    }

    /** price는 정가, salePrice는 진행 중인 세일을 적용한 가격이다. 세일이 없으면 둘이 같다. */
    public record PublicSku(Long id, String name, long price, long salePrice, boolean soldOut) {

        static PublicSku of(Sku sku, Product product, Instant now) {
            return new PublicSku(sku.getId(), sku.getName(), product.unitPrice(sku),
                    product.unitSalePrice(sku, now), sku.isSoldOut());
        }
    }

    /** 모든 옵션이 품절이면 상품도 품절이다. 세일은 {@code now}에 진행 중인 것만 반영한다. */
    public static ProductResponse of(Product product, Brand brand, List<Category> categoryPath, List<Sku> skus,
            Instant now) {
        Optional<Sale> activeSale = product.activeSale(now);
        return new ProductResponse(product.getId(), product.getName(),
                new BrandSummary(brand.getId(), brand.getName()),
                categoryPath.stream().map(c -> new CategorySummary(c.getId(), c.getName())).toList(),
                product.getSummary(), product.getDescription(), product.getIngredients(),
                List.copyOf(product.getImageUrls()), product.getBasePrice(),
                activeSale.map(Sale::getRate).orElse(null),
                skus.stream().allMatch(Sku::isSoldOut),
                skus.stream().map(sku -> PublicSku.of(sku, product, now)).toList(),
                product.getRatingAvg(), product.getReviewCount());
    }
}
