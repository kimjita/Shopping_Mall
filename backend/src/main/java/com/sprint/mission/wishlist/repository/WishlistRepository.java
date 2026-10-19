package com.sprint.mission.wishlist.repository;

import com.sprint.mission.wishlist.domain.Wishlist;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    /**
     * 이미 찜했으면 아무것도 하지 않는다. 트랜잭션 안에서 unique 위반 예외를 잡으면 rollback-only가 되므로
     * 예외 대신 {@code on conflict}로 멱등하게 만든다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into wishlist (member_id, product_id, created_at, updated_at) "
            + "values (:memberId, :productId, :now, :now) on conflict (member_id, product_id) do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("productId") Long productId,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Wishlist w where w.memberId = :memberId and w.productId = :productId")
    int deleteByMemberIdAndProductId(Long memberId, Long productId);

    /** 회원 탈퇴 때 이 회원의 찜을 모두 지운다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from Wishlist w where w.memberId = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);

    List<Wishlist> findByMemberIdOrderByIdDesc(Long memberId, Limit limit);

    List<Wishlist> findByMemberIdAndIdLessThanOrderByIdDesc(Long memberId, Long id, Limit limit);

    @Query("select w.productId from Wishlist w where w.memberId = :memberId and w.productId in :productIds")
    List<Long> findProductIds(Long memberId, Collection<Long> productIds);
}
