package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.product.dto.CategoryResponse;
import com.sprint.mission.product.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리는 약 40행이라 처음 쓸 때 한 번만 읽어 메모리에 들고 있는다.
 * Flyway 시드이고 런타임에 바뀌지 않으므로 갱신하지 않는다. 바뀌면 재시작해서 다시 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private volatile Snapshot snapshot;

    /** 루트부터 시작하는 전체 트리다. 형제는 sort_order 순서다. */
    public List<CategoryResponse> tree() {
        Snapshot s = snapshot();
        return toResponses(s.childrenByParent().get(null), s.childrenByParent());
    }

    /** 가장 아래 단계 카테고리를 돌려준다. 없으면 CATEGORY_NOT_FOUND, 자식이 있으면 CATEGORY_NOT_LEAF다. */
    public Category getLeaf(Long categoryId) {
        Snapshot s = snapshot();
        Category category = find(s, categoryId);
        if (s.childrenByParent().containsKey(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_LEAF);
        }
        return category;
    }

    /** 루트부터 해당 카테고리까지의 경로다. 없으면 CATEGORY_NOT_FOUND다. */
    public List<Category> path(Long categoryId) {
        Snapshot s = snapshot();
        List<Category> path = new ArrayList<>();
        Category current = find(s, categoryId);
        while (current != null) {
            path.addFirst(current);
            Long parentId = current.getParentId();
            current = parentId == null ? null : find(s, parentId);
        }
        return path;
    }

    /**
     * 해당 카테고리 아래의 모든 말단 id다. 자기 자신이 말단이면 자기 id만 돌려준다.
     * 형제는 sort_order 순서로 깊이 우선 탐색한다. 없으면 CATEGORY_NOT_FOUND다.
     */
    public List<Long> leafIdsUnder(Long categoryId) {
        Snapshot s = snapshot();
        find(s, categoryId);
        List<Long> leaves = new ArrayList<>();
        collectLeaves(categoryId, s.childrenByParent(), leaves);
        return List.copyOf(leaves);
    }

    private static void collectLeaves(Long categoryId, Map<Long, List<Category>> childrenByParent,
                                      List<Long> leaves) {
        List<Category> children = childrenByParent.get(categoryId);
        if (children == null) {
            leaves.add(categoryId);
            return;
        }
        children.forEach(child -> collectLeaves(child.getId(), childrenByParent, leaves));
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = Snapshot.of(categoryRepository.findAll());
                    snapshot = s;
                }
            }
        }
        return s;
    }

    private static Category find(Snapshot s, Long categoryId) {
        Category category = s.byId().get(categoryId);
        if (category == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
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

    /** 전체 카테고리의 불변 사본이다. 자식 목록은 sort_order 순서이며 루트의 parentId는 null 키다. */
    private record Snapshot(Map<Long, Category> byId, Map<Long, List<Category>> childrenByParent) {

        static Snapshot of(List<Category> all) {
            Map<Long, Category> byId = new HashMap<>();
            Map<Long, List<Category>> children = new HashMap<>();
            all.stream()
                    .sorted(Comparator.comparingInt(Category::getSortOrder))
                    .forEach(c -> {
                        byId.put(c.getId(), c);
                        children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
                    });
            children.replaceAll((k, v) -> List.copyOf(v));
            return new Snapshot(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(children));
        }
    }
}
