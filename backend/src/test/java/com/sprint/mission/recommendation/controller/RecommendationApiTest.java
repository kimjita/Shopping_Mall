package com.sprint.mission.recommendation.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.recommendation.RecommendationFixtures;
import com.sprint.mission.recommendation.service.RecommendationService;
import com.sprint.mission.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;

class RecommendationApiTest extends ApiTest {

    @Autowired
    RecommendationService recommendationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    RecommendationFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new RecommendationFixtures(jdbcTemplate);
    }

    @Test
    void 피부타입별_추천을_순위대로_읽는다() throws Exception {
        Long second = fixtures.product("두 번째");
        fixtures.reviews(second, "OILY", 4, 5);
        Long first = fixtures.product("첫 번째");
        fixtures.reviews(first, "OILY", 5, 6);
        Long dry = fixtures.product("건성용");
        fixtures.reviews(dry, "DRY", 5, 5);
        recommendationService.rebuild();

        mockMvc.perform(get("/api/recommendations").param("skinType", "OILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").value("OILY"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].product.id").value(contains(first.intValue(), second.intValue())))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].sameSkinReviewCount").value(6))
                .andExpect(jsonPath("$.items[0].sameSkinRatingAvg").value(5.0))
                .andExpect(jsonPath("$.items[1].rank").value(2))
                .andDo(document("recommendation/list",
                        queryParameters(parameterWithName("skinType")
                                .description("피부 타입. `DRY`, `OILY`, `COMBINATION`, `SENSITIVE`, `NORMAL` 중 하나다")),
                        responseFields(
                                fieldWithPath("skinType").type(JsonFieldType.STRING).description("요청한 피부 타입"),
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("추천 목록. 최대 20개다"),
                                fieldWithPath("items[].rank").type(JsonFieldType.NUMBER)
                                        .description("배치가 매긴 순위. 1부터 시작한다. 배치 뒤 판매가 중지된 상품이 빠지면 건너뛰는 번호가 있을 수 있다"),
                                fieldWithPath("items[].sameSkinReviewCount").type(JsonFieldType.NUMBER)
                                        .description("같은 피부 타입 회원이 쓴 리뷰 수. 5 이상이다"),
                                fieldWithPath("items[].sameSkinRatingAvg").type(JsonFieldType.NUMBER)
                                        .description("같은 피부 타입 리뷰의 평균 평점. 소수 둘째 자리까지 반올림한 값이다"),
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
    void 배치_뒤_판매_중지된_상품은_조회에서_빠진다() throws Exception {
        Long stopped = fixtures.product("나중에 중지");
        fixtures.reviews(stopped, "COMBINATION", 5, 5);
        Long selling = fixtures.product("계속 판매");
        fixtures.reviews(selling, "COMBINATION", 4, 5);
        recommendationService.rebuild();
        fixtures.stop(stopped);

        mockMvc.perform(get("/api/recommendations").param("skinType", "COMBINATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].product.id").value(contains(selling.intValue())));
    }

    @Test
    void skinType이_없거나_틀리면_400() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/recommendations").param("skinType", "SHINY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰_없이도_읽는다() throws Exception {
        mockMvc.perform(get("/api/recommendations").param("skinType", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }
}
