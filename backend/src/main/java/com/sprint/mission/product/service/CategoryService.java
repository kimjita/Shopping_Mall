package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.product.dto.CategoryResponse;
import com.sprint.mission.product.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리는 약 40행이라 매번 전체를 읽어 메모리에서 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /** 루트부터 시작하는 전체 트리다. 형제는 sort_order 순서다. */
    public List<CategoryResponse> tree() {
        List<Category> all = categoryRepository.findAll();
        Map<Long, List<Category>> childrenByParent = new LinkedHashMap<>();
        all.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .forEach(c -> childrenByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c));
        return toResponses(childrenByParent.get(null), childrenByParent);
    }

    /** 가장 아래 단계 카테고리를 돌려준다. 없으면 CATEGORY_NOT_FOUND, 자식이 있으면 CATEGORY_NOT_LEAF다. */
    public Category getLeaf(Long categoryId) {
        List<Category> all = categoryRepository.findAll();
        Category category = find(all, categoryId);
        boolean hasChildren = all.stream().anyMatch(c -> categoryId.equals(c.getParentId()));
        if (hasChildren) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_LEAF);
        }
        return category;
    }

    /** 루트부터 해당 카테고리까지의 경로다. 없으면 CATEGORY_NOT_FOUND다. */
    public List<Category> path(Long categoryId) {
        List<Category> all = categoryRepository.findAll();
        List<Category> path = new ArrayList<>();
        Category current = find(all, categoryId);
        while (current != null) {
            path.addFirst(current);
            Long parentId = current.getParentId();
            current = parentId == null ? null : find(all, parentId);
        }
        return path;
    }

    private static Category find(List<Category> all, Long categoryId) {
        return all.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private static List<CategoryResponse> toResponses(List<Category> categories,
                                                      Map<Long, List<Category>> childrenByParent) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(),
                        toResponses(childrenByParent.get(c.getId()), childrenByParent)))
                .toList();
    }
}
