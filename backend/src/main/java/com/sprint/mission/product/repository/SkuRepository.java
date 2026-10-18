package com.sprint.mission.product.repository;

import com.sprint.mission.product.domain.Sku;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkuRepository extends JpaRepository<Sku, Long> {

    List<Sku> findAllByProductIdOrderByIdAsc(Long productId);

    Optional<Sku> findByIdAndProductId(Long id, Long productId);

    long countByProductId(Long productId);

    boolean existsByProductIdAndName(Long productId, String name);

    boolean existsByProductIdAndNameAndIdNot(Long productId, String name, Long id);

    /**
     * 결과 재고가 0 이상 100,000 이하일 때만 재고를 바꾼다. 읽고 계산해서 쓰는 대신 DB가 한 문장으로 계산하므로
     * 동시에 호출해도 행 잠금 때문에 재고가 음수가 되거나 갱신이 사라지지 않는다. 바뀐 행이 없으면 0을 돌려준다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Sku s set s.stock = s.stock + :delta, s.updatedAt = :now
            where s.id = :id and s.stock + :delta >= 0 and s.stock + :delta <= 100000
            """)
    int adjustStock(@Param("id") Long id, @Param("delta") int delta, @Param("now") Instant now);
}
