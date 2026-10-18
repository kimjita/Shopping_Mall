package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CategoryServiceTest extends IntegrationTest {

    @Autowired
    CategoryService categoryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long idOf(String name) {
        return jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
    }

    @Test
    void 말단_카테고리를_돌려준다() {
        Category leaf = categoryService.getLeaf(idOf("립스틱"));

        assertThat(leaf.getName()).isEqualTo("립스틱");
        assertThat(leaf.getDepth()).isEqualTo(3);
    }

    @Test
    void 자식이_있는_카테고리는_말단이_아니다() {
        assertThatThrownBy(() -> categoryService.getLeaf(idOf("립")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_LEAF);
        assertThatThrownBy(() -> categoryService.getLeaf(idOf("스킨케어")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_LEAF);
    }

    @Test
    void 없는_카테고리는_찾을_수_없다() {
        assertThatThrownBy(() -> categoryService.getLeaf(9999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
        assertThatThrownBy(() -> categoryService.path(9999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void 경로는_루트부터_해당_카테고리까지다() {
        assertThat(categoryService.path(idOf("립스틱")))
                .extracting(Category::getName)
                .containsExactly("메이크업", "립", "립스틱");
        assertThat(categoryService.path(idOf("토너")))
                .extracting(Category::getName)
                .containsExactly("스킨케어", "토너");
        assertThat(categoryService.path(idOf("헤어케어")))
                .extracting(Category::getName)
                .containsExactly("헤어케어");
    }
}
