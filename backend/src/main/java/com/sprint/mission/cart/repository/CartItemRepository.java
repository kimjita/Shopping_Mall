package com.sprint.mission.cart.repository;

import com.sprint.mission.cart.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberIdOrderByIdAsc(Long memberId);

    Optional<CartItem> findByMemberIdAndSkuId(Long memberId, Long skuId);

    Optional<CartItem> findByIdAndMemberId(Long id, Long memberId);

    long countByMemberId(Long memberId);
}
