package com.sprint.mission.product.repository;

import com.sprint.mission.product.domain.Sku;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkuRepository extends JpaRepository<Sku, Long> {

    List<Sku> findAllByProductIdOrderByIdAsc(Long productId);
}
