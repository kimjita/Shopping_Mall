package com.sprint.mission.product.repository;

import com.sprint.mission.product.domain.Product;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 브랜드 이름이 바뀔 때 그 브랜드 상품 전체의 검색 문자열을 한 문장으로 다시 만든다. 바뀐 행 수를 돌려준다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p set p.searchText = concat(:brandName, ' ', p.name), p.updatedAt = :now
            where p.brandId = :brandId
            """)
    int refreshSearchTextByBrand(@Param("brandId") Long brandId, @Param("brandName") String brandName,
            @Param("now") Instant now);
}
