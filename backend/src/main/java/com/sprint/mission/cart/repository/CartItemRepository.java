package com.sprint.mission.cart.repository;

import com.sprint.mission.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberIdOrderByIdAsc(Long memberId);

    Optional<CartItem> findByMemberIdAndSkuId(Long memberId, Long skuId);

    Optional<CartItem> findByIdAndMemberId(Long id, Long memberId);

    /** 이 회원의 줄만 돌려준다. 다른 회원의 id가 섞여 있으면 그 줄은 빠진다. */
    List<CartItem> findAllByMemberIdAndIdIn(Long memberId, Collection<Long> ids);

    long countByMemberId(Long memberId);
}
