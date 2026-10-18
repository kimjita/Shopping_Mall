package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.Brand;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import java.util.List;

/** 고객에게 보여 주는 상품 상세다. 재고 수량은 내려주지 않고 품절 여부만 알린다. */
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
        boolean soldOut,
        List<PublicSku> skus) {

    public record BrandSummary(Long id, String name) {
    }

    public record CategorySummary(Long id, String name) {
    }

    public record PublicSku(Long id, String name, long price, boolean soldOut) {

        static PublicSku of(Sku sku, long basePrice) {
            return new PublicSku(sku.getId(), sku.getName(), sku.price(basePrice), sku.isSoldOut());
        }
    }

    /** 모든 옵션이 품절이면 상품도 품절이다. */
    public static ProductResponse of(Product product, Brand brand, List<Category> categoryPath, List<Sku> skus) {
        return new ProductResponse(product.getId(), product.getName(),
                new BrandSummary(brand.getId(), brand.getName()),
                categoryPath.stream().map(c -> new CategorySummary(c.getId(), c.getName())).toList(),
                product.getSummary(), product.getDescription(), product.getIngredients(),
                List.copyOf(product.getImageUrls()), product.getBasePrice(),
                skus.stream().allMatch(Sku::isSoldOut),
                skus.stream().map(sku -> PublicSku.of(sku, product.getBasePrice())).toList());
    }
}
