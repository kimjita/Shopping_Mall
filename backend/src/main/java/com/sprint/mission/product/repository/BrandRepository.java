package com.sprint.mission.product.repository;

import com.sprint.mission.product.domain.Brand;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Brand> findAllByOrderByNameAsc();
}
