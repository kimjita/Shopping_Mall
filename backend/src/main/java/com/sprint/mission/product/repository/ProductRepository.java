package com.sprint.mission.product.repository;

import com.sprint.mission.product.domain.Product;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 행 잠금을 걸고 읽는다. 최저가처럼 옵션 전체에서 다시 계산하는 값을 동시에 고치지 못하게 관리자 수정끼리 직렬화한다.
     * 잠금은 {@code for no key update}다. {@code for update}는 장바구니·찜·주문상품 insert가 상품에 거는 외래 키 잠금
     * ({@code key share})을 막아, 옵션을 먼저 잠근 주문과 상품을 먼저 잠근 옵션 수정이 서로를 기다리는 데드락이 난다.
     * Hibernate 번역에 기대지 않도록 잠금 세기를 SQL에 직접 적는다.
     */
    @Query(value = "select * from product where id = :id for no key update", nativeQuery = true)
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /** 브랜드 이름이 바뀔 때 그 브랜드 상품 전체의 검색 문자열을 한 문장으로 다시 만든다. 바뀐 행 수를 돌려준다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p set p.searchText = concat(:brandName, ' ', p.name), p.updatedAt = :now
            where p.brandId = :brandId
            """)
    int refreshSearchTextByBrand(@Param("brandId") Long brandId, @Param("brandName") String brandName,
            @Param("now") Instant now);

    /**
     * 리뷰 수·별점 합계·평균을 한 문장으로 바꾼다. 쓰기는 (+1, +별점), 수정은 (0, 새 별점 - 옛 별점), 삭제는 (-1, -별점)이다.
     * 읽고 계산해 쓰면 동시에 쓴 리뷰끼리 서로의 값을 덮어쓰므로, 행 잠금 아래에서 현재 값에 더한다. 호출자의 트랜잭션이 필요하다.
     * 리뷰가 하나도 남지 않으면 평균은 NULL이다. {@code updated_at}은 건드리지 않는다. 바뀐 행 수를 돌려준다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update product set review_count = review_count + :countDelta, rating_sum = rating_sum + :sumDelta,
                   rating_avg = case when review_count + :countDelta = 0 then null
                                     else round((rating_sum + :sumDelta)::numeric / (review_count + :countDelta), 2) end
            where id = :productId
            """, nativeQuery = true)
    int changeRating(@Param("productId") Long productId, @Param("countDelta") int countDelta,
            @Param("sumDelta") int sumDelta);
}
