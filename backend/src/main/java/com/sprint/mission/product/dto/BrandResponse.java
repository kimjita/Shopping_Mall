package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.Brand;

public record BrandResponse(Long id, String name, String description) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(brand.getId(), brand.getName(), brand.getDescription());
    }
}
