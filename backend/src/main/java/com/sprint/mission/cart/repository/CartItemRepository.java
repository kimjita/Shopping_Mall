package com.sprint.mission.cart.repository;

import com.sprint.mission.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberIdOrderByIdAsc(Long memberId);

    Optional<CartItem> findByMemberIdAndSkuId(Long memberId, Long skuId);

    Optional<CartItem> findByIdAndMemberId(Long id, Long memberId);

    /** 이 회원의 줄만 돌려준다. 다른 회원의 id가 섞여 있으면 그 줄은 빠진다. */
    List<CartItem> findAllByMemberIdAndIdIn(Long memberId, Collection<Long> ids);

    long countByMemberId(Long memberId);

    /** 회원 탈퇴 때 이 회원의 장바구니를 모두 지운다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem c where c.memberId = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);

    /** 결제 승인 때 주문한 SKU의 줄만 지운다. 다른 회원과 주문하지 않은 SKU의 줄은 남는다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem c where c.memberId = :memberId and c.skuId in :skuIds")
    int deleteByMemberIdAndSkuIdIn(@Param("memberId") Long memberId, @Param("skuIds") Collection<Long> skuIds);
}
