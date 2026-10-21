package com.sprint.mission.product.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 판매 상품이다. 브랜드·카테고리는 id로만 참조하고, 옵션은 {@link Sku}가 상품 id를 들고 있다.
 * 상태·세일·최저가를 서로 다른 요청이 고치므로, 읽어 둔 낡은 값이 다른 컬럼을 덮어쓰지 않도록 바뀐 컬럼만 갱신한다.
 */
@Getter
@Entity
@DynamicUpdate
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

    /** 옵션 중 가장 싼 정가(정가 + 추가금액)다. 목록 정렬·가격 필터용으로 옵션이 바뀔 때마다 다시 계산한다. */
    @Column(nullable = false)
    private long minPrice;

    /** 검색용 문자열("브랜드명 상품명")이다. 상품이나 브랜드 이름이 바뀔 때마다 다시 만든다. */
    @Column(nullable = false, columnDefinition = "text")
    private String searchText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /** 첫 번째가 대표 이미지다. */
    @ElementCollection
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, length = 500)
    private List<String> imageUrls = new ArrayList<>();

    /** 리뷰 수·별점 합계·평균은 리뷰를 쓰고 고치고 지울 때 벌크 SQL로만 바꾼다. 평균은 리뷰가 없으면 null이다. */
    @Column(insertable = false, updatable = false)
    private int reviewCount;

    @Column(insertable = false, updatable = false)
    private int ratingSum;

    @Column(insertable = false, updatable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    /** 걸린 세일이다. 컬럼이 모두 NULL이면 Hibernate가 null로 읽으므로 null을 "세일 없음"으로 본다. */
    @Embedded
    private Sale sale;

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
        // 옵션과 브랜드 이름은 저장 뒤에야 알 수 있으므로 임시 값을 두고, 서비스가 곧바로 refresh로 맞춘다.
        product.minPrice = basePrice;
        product.searchText = name;
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

    /** 옵션 가격 중 가장 싼 값으로 최저가를 다시 계산한다. 옵션이 하나도 없으면 계산할 수 없다. */
    public void refreshMinPrice(List<Sku> skus) {
        this.minPrice = skus.stream()
                .mapToLong(this::unitPrice)
                .min()
                .orElseThrow(() -> new IllegalStateException("옵션이 없는 상품의 최저가는 계산할 수 없다."));
    }

    public void refreshSearchText(String brandName) {
        this.searchText = brandName + " " + name;
    }

    /** 삭제된 상품은 상태를 바꿀 수 없다. */
    public void changeStatus(ProductStatus status) {
        if (isDeleted()) {
            throw new IllegalStateException("삭제된 상품의 상태는 바꿀 수 없다.");
        }
        this.status = status;
    }

    /** 세일을 건다. 기존 세일은 통째로 교체된다. */
    public void startSale(Sale sale) {
        this.sale = sale;
    }

    public void endSale() {
        this.sale = null;
    }

    /** 지금 진행 중인 세일이다. 세일이 없거나 기간이 아니면 비어 있다. */
    public Optional<Sale> activeSale(Instant now) {
        return Optional.ofNullable(sale).filter(s -> s.isActive(now));
    }

    /** 옵션 정가다. 상품 정가에 옵션 추가금액을 더한다. */
    public long unitPrice(Sku sku) {
        return sku.price(basePrice);
    }

    /** 옵션 판매가다. 진행 중인 세일이 있으면 옵션 정가에 적용하고, 없으면 옵션 정가 그대로다. */
    public long unitSalePrice(Sku sku, Instant now) {
        long unitPrice = unitPrice(sku);
        return activeSale(now).map(s -> s.apply(unitPrice)).orElse(unitPrice);
    }
}
