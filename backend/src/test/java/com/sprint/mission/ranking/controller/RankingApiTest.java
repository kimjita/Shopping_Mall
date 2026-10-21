package com.sprint.mission.ranking.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.repository.ProductSearchRepository;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RankingApiTest extends ApiTest {

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    ProductSearchRepository productSearchRepository;

    Long brandId;
    Long tonerId;
    Long creamId;
    Long cushionId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        tonerId = categoryId("토너");
        creamId = categoryId("크림");
        cushionId = categoryId("쿠션");
    }

    private Long categoryId(String name) {
        return jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
    }

    private Long register(Long categoryId, String name, int sales7d) {
        Long id = adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, 10_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
        jdbcTemplate.update("update product set sales_7d = ? where id = ?", sales7d, id);
        return id;
    }

    @Test
    void 판매량_순으로_20개까지_순위를_매긴다() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            ids.add(register(tonerId, "상품 " + i, 10 + i / 2)); // 동률이 쌍으로 생긴다
        }
        // 판매량 20(i=20,21), 19(18,19), ... 동률이면 id 내림차순이다.
        List<Integer> expected = new ArrayList<>();
        for (int i = 21; i >= 2; i--) {
            expected.add(ids.get(i).intValue());
        }

        mockMvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[*].product.id").value(contains(expected.toArray())))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[19].rank").value(20))
                .andDo(document("ranking/list",
                        queryParameters(parameterWithName("categoryId").optional()
                                .description("카테고리 ID. 어느 단계든 받고, 그 아래 가장 아래 단계 카테고리 전체의 상품으로 순위를 매긴다. 없으면 전체다")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("순위 목록. 최대 20개다"),
                                fieldWithPath("items[].rank").type(JsonFieldType.NUMBER).description("순위. 1부터 시작한다"),
                                fieldWithPath("items[].product").type(JsonFieldType.OBJECT).description("상품 카드"),
                                fieldWithPath("items[].product.id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].product.name").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("items[].product.brandName").type(JsonFieldType.STRING)
                                        .description("브랜드 이름"),
                                fieldWithPath("items[].product.thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("대표 이미지 URL. 상품에 이미지가 없으면 null이다"),
                                fieldWithPath("items[].product.price").type(JsonFieldType.NUMBER)
                                        .description("정가 (원). 옵션 중 가장 싼 정가다"),
                                fieldWithPath("items[].product.salePrice").type(JsonFieldType.NUMBER)
                                        .description("현재 판매가 (원). 세일 중이면 할인율을 적용해 10원 단위로 내린 값이고, 아니면 정가와 같다"),
                                fieldWithPath("items[].product.saleRate").type(JsonFieldType.NUMBER).optional()
                                        .description("진행 중인 세일의 할인율 (%). 진행 중인 세일이 없으면 `null`"),
                                fieldWithPath("items[].product.soldOut").type(JsonFieldType.BOOLEAN)
                                        .description("품절 여부. 모든 옵션이 품절이면 `true`다"),
                                fieldWithPath("items[].product.ratingAvg").type(JsonFieldType.NUMBER).optional()
                                        .description("평균 평점. 소수 둘째 자리까지 반올림한 값이다. 리뷰가 없으면 `null`"),
                                fieldWithPath("items[].product.reviewCount").type(JsonFieldType.NUMBER)
                                        .description("리뷰 수"))));
    }

    @Test
    void 판매가_없거나_판매_중지된_상품은_빠진다() throws Exception {
        Long selling = register(tonerId, "판매 중", 3);
        register(tonerId, "판매량 없음", 0);
        Long stopped = register(tonerId, "판매 중지", 9);
        Long deleted = register(tonerId, "삭제", 8);
        jdbcTemplate.update("update product set status = ? where id = ?", ProductStatus.STOPPED.name(), stopped);
        jdbcTemplate.update("update product set status = ? where id = ?", ProductStatus.DELETED.name(), deleted);

        mockMvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].product.id").value(contains(selling.intValue())));
    }

    @Test
    void 순위_쿼리_뒤에_판매_중지된_상품은_카드_조회에서_걸러_순위가_이어진다() throws Exception {
        Long first = register(tonerId, "1등", 9);
        Long second = register(tonerId, "2등", 8);
        Long third = register(tonerId, "3등", 7);
        // id 쿼리와 카드 쿼리 사이에 2등이 판매 중지된 경우를 흉내 낸다.
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<Long, ProductCard> real = (Map<Long, ProductCard>) invocation.callRealMethod();
            Map<Long, ProductCard> cards = new LinkedHashMap<>(real);
            cards.put(second, new ProductCard(real.get(second).summary(), ProductStatus.STOPPED));
            return cards;
        }).when(productSearchRepository).findCards(any(), any());

        mockMvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].product.id").value(contains(first.intValue(), third.intValue())))
                .andExpect(jsonPath("$.items[*].rank").value(contains(1, 2)));
    }

    @Test
    void 카테고리를_주면_하위_카테고리까지_포함한다() throws Exception {
        Long toner = register(tonerId, "토너", 5);
        Long cream = register(creamId, "크림", 7);
        register(cushionId, "쿠션", 100);

        mockMvc.perform(get("/api/rankings").param("categoryId", categoryId("스킨케어").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].product.id").value(contains(cream.intValue(), toner.intValue())))
                .andExpect(jsonPath("$.items[1].rank").value(2));
        mockMvc.perform(get("/api/rankings").param("categoryId", tonerId.toString()))
                .andExpect(jsonPath("$.items[*].product.id").value(contains(toner.intValue())));
    }

    @Test
    void 없는_카테고리는_404() throws Exception {
        mockMvc.perform(get("/api/rankings").param("categoryId", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void 토큰_없이도_읽는다() throws Exception {
        mockMvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }
}
