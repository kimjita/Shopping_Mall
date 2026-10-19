package com.sprint.mission.product.repository;

import com.sprint.mission.product.domain.Sku;
import java.time.Instant;
import java.util.Collection;
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
     * 호출한 트랜잭션이 읽어 둔 다른 엔티티가 떨어져 나가지 않도록 영속성 컨텍스트는 비우지 않는다.
     * 그래서 이 트랜잭션 안의 {@code Sku.stock}은 낡은 값일 수 있으며, 바뀐 재고는 {@link #findStockById}로 읽는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Sku s set s.stock = s.stock + :delta, s.updatedAt = :now
            where s.id = :id and s.stock + :delta >= 0 and s.stock + :delta <= 100000
            """)
    int adjustStock(@Param("id") Long id, @Param("delta") int delta, @Param("now") Instant now);

    /**
     * 옵션 행들을 id 오름차순으로 한 문장에서 잠그고 잠근 id를 돌려준다. 재고 UPDATE와 같은 세기의 잠금
     * ({@code for no key update})이라 주문상품 insert의 외래 키 잠금({@code key share})과는 부딪히지 않는다.
     */
    @Query(value = "select id from sku where id in (:ids) order by id for no key update", nativeQuery = true)
    List<Long> lockAllForUpdate(@Param("ids") Collection<Long> ids);

    /** 영속성 컨텍스트를 거치지 않고 DB의 현재 재고를 읽는다. 옵션이 없으면 비어 있다. */
    @Query("select s.stock from Sku s where s.id = :id")
    Optional<Integer> findStockById(@Param("id") Long id);

    /**
     * 재고를 {@code quantity}만큼 되돌린다. 주문 취소·만료로 이미 차감했던 수량을 복원하는 용도라 상한이 없다.
     * 수량은 양수여야 하며 아니면 {@link IllegalArgumentException}이다. 바뀐 행 수를 돌려준다.
     */
    default int restoreStock(Long id, int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복원 수량은 양수여야 한다: " + quantity);
        }
        return addStock(id, quantity, now);
    }

    /** {@link #restoreStock}의 실제 UPDATE다. 수량 검사를 거치지 않으므로 직접 호출하지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query("update Sku s set s.stock = s.stock + :quantity, s.updatedAt = :now where s.id = :id")
    int addStock(@Param("id") Long id, @Param("quantity") int quantity, @Param("now") Instant now);
}
