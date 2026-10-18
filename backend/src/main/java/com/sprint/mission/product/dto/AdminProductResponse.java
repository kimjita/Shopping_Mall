package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.domain.Sku;
import java.util.List;

public record AdminProductResponse(
        Long id,
        ProductStatus status,
        Long brandId,
        String brandName,
        List<String> categoryPath,
        String name,
        String summary,
        String description,
        String ingredients,
        long basePrice,
        List<String> imageUrls,
        List<AdminSku> skus) {

    public record AdminSku(Long id, String name, long additionalPrice, long price, int stock) {

        static AdminSku of(Sku sku, long basePrice) {
            return new AdminSku(sku.getId(), sku.getName(), sku.getAdditionalPrice(), sku.price(basePrice),
                    sku.getStock());
        }
    }

    public static AdminProductResponse of(Product product, String brandName, List<String> categoryPath,
            List<Sku> skus) {
        return new AdminProductResponse(product.getId(), product.getStatus(), product.getBrandId(), brandName,
                categoryPath, product.getName(), product.getSummary(), product.getDescription(),
                product.getIngredients(), product.getBasePrice(), List.copyOf(product.getImageUrls()),
                skus.stream().map(sku -> AdminSku.of(sku, product.getBasePrice())).toList());
    }
}
