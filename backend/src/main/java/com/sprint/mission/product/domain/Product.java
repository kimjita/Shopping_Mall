package com.sprint.mission.product.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판매 상품이다. 브랜드·카테고리는 id로만 참조하고, 옵션은 {@link Sku}가 상품 id를 들고 있다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String ingredients;

    @Column(nullable = false)
    private long basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /** 첫 번째가 대표 이미지다. */
    @ElementCollection
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, length = 500)
    private List<String> imageUrls = new ArrayList<>();

    public static Product create(Long brandId, Long categoryId, String name, String summary, String description,
            String ingredients, long basePrice, List<String> imageUrls) {
        Product product = new Product();
        product.brandId = brandId;
        product.categoryId = categoryId;
        product.name = name;
        product.summary = summary;
        product.description = description;
        product.ingredients = ingredients;
        product.basePrice = basePrice;
        product.status = ProductStatus.ON_SALE;
        product.imageUrls = new ArrayList<>(imageUrls);
        return product;
    }

    /** 공개 조회에 보이는 상태인지 여부다. */
    public boolean isVisibleToPublic() {
        return status == ProductStatus.ON_SALE;
    }

    public boolean isDeleted() {
        return status == ProductStatus.DELETED;
    }

    /** 상품 정보를 바꾼다. 이미지 목록은 통째로 교체한다. */
    public void update(Long brandId, Long categoryId, String name, String summary, String description,
            String ingredients, long basePrice, List<String> imageUrls) {
        this.brandId = brandId;
        this.categoryId = categoryId;
        this.name = name;
        this.summary = summary;
        this.description = description;
        this.ingredients = ingredients;
        this.basePrice = basePrice;
        this.imageUrls.clear();
        this.imageUrls.addAll(imageUrls);
    }

    /** 삭제된 상품은 상태를 바꿀 수 없다. */
    public void changeStatus(ProductStatus status) {
        if (isDeleted()) {
            throw new IllegalStateException("삭제된 상품의 상태는 바꿀 수 없다.");
        }
        this.status = status;
    }
}
