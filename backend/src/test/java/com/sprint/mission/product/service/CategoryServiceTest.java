package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Category;
import com.sprint.mission.support.IntegrationTest;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManagerFactory;

class CategoryServiceTest extends IntegrationTest {

    @Autowired
    CategoryService categoryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

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

    @Test
    void 말단은_자기_자신만_돌려준다() {
        Long toner = idOf("토너");

        assertThat(categoryService.leafIdsUnder(toner)).containsExactly(toner);
    }

    @Test
    void 대분류는_아래_말단을_모두_돌려준다() {
        List<Long> expected = List.of(idOf("토너"), idOf("에센스·세럼"), idOf("크림"), idOf("로션"), idOf("미스트·오일"));

        assertThat(categoryService.leafIdsUnder(idOf("스킨케어"))).containsExactlyElementsOf(expected);
    }

    @Test
    void 세_단계_대분류도_말단까지_내려간다() {
        List<Long> leaves = categoryService.leafIdsUnder(idOf("메이크업"));

        assertThat(leaves).hasSize(9);
        assertThat(leaves).contains(idOf("립스틱"));
        assertThat(leaves).doesNotContain(idOf("립"), idOf("베이스"), idOf("아이"));
    }

    @Test
    void 없는_카테고리의_하위_말단_조회는_404다() {
        assertThatThrownBy(() -> categoryService.leafIdsUnder(9999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void 여러_번_불러도_카테고리를_다시_읽지_않는다() {
        Long makeup = idOf("메이크업");
        Long lipstick = idOf("립스틱");
        categoryService.tree(); // 캐시를 채운다
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        categoryService.tree();
        categoryService.leafIdsUnder(makeup);
        categoryService.getLeaf(lipstick);
        categoryService.path(lipstick);

        assertThat(statistics.getPrepareStatementCount()).isZero();
    }
}
