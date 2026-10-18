package com.sprint.mission.product.dto;

import java.util.List;

public record CategoryResponse(Long id, String name, List<CategoryResponse> children) {
}
