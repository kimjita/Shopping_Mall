package com.sprint.mission.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.review.ReviewFixtures;
import com.sprint.mission.support.ApiTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(ReviewFixtures.class)
class ReviewWriteApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");
    private static final String CONTENT = "촉촉하고 순해서 매일 쓰기 좋아요.";

    @Autowired
    ReviewFixtures fixtures;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long productId;
    Long memberId;

    @BeforeEach
    void setUp() {
        productId = fixtures.product("독도 토너");
        memberId = fixtures.member("user@mall.com");
    }

    private String token(Long id) {
        return bearer(id, Role.USER);
    }

    private Map<String, Object> body(Long orderItemId, int rating, String content, List<String> imageUrls) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderItemId", orderItemId);
        body.put("rating", rating);
        body.put("content", content);
        body.put("imageUrls", imageUrls);
        return body;
    }

    private Map<String, Object> editBody(int rating, String content, List<String> imageUrls) {
        Map<String, Object> body = body(null, rating, content, imageUrls);
        body.remove("orderItemId");
        return body;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private Long write(Long ownerId, Long orderItemId, int rating) throws Exception {
        String response = send(post("/api/members/me/reviews"), token(ownerId), body(orderItemId, rating, CONTENT,
                List.of())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("reviewId").asLong();
    }

    /** 상품 집계 컬럼을 "개수/합계/평균" 문자열로 읽는다. */
    private String aggregate() {
        return jdbcTemplate.queryForObject("select review_count || '/' || rating_sum || '/' || coalesce(rating_avg::text, 'null') "
                + "from product where id = ?", String.class, productId);
    }

    private static FieldDescriptor[] reviewFields() {
        return new FieldDescriptor[] {
                fieldWithPath("reviewId").type(JsonFieldType.NUMBER).description("리뷰 ID"),
                fieldWithPath("orderItemId").type(JsonFieldType.NUMBER).description("리뷰를 쓴 주문상품 ID"),
                fieldWithPath("productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                fieldWithPath("productName").type(JsonFieldType.STRING).description("주문 시점의 상품명"),
                fieldWithPath("skuName").type(JsonFieldType.STRING).description("주문 시점의 옵션명"),
                fieldWithPath("thumbnailUrl").type(JsonFieldType.STRING).optional()
                        .description("주문 시점의 썸네일 URL. 없으면 `null`"),
                fieldWithPath("rating").type(JsonFieldType.NUMBER).description("별점 (1~5)"),
                fieldWithPath("content").type(JsonFieldType.STRING).description("본문. 앞뒤 공백을 뗀 값이다"),
                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY).description("사진 URL. 보낸 순서 그대로이고 첫 번째가 대표 사진이다"),
                fieldWithPath("skinType").type(JsonFieldType.STRING).optional()
                        .description("작성 시점 회원의 피부 타입. 없으면 `null`"),
                fieldWithPath("skinTone").type(JsonFieldType.STRING).optional()
                        .description("작성 시점 회원의 피부 톤. 없으면 `null`"),
                fieldWithPath("skinConcerns").type(JsonFieldType.ARRAY)
                        .description("작성 시점 회원의 피부 고민. enum 이름순이다"),
                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("작성 시각"),
                fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("마지막 수정 시각")};
    }

    @Test
    void 구매확정_상품에_리뷰를_쓰면_상품_평점이_오른다() throws Exception {
        memberService.updateSkinProfile(memberId, SkinType.OILY, SkinTone.WARM,
                List.of(SkinConcern.TROUBLE, SkinConcern.PORE));
        Long orderItemId = fixtures.orderItem(memberId, productId, "CONFIRMED");

        String response = send(post("/api/members/me/reviews"), token(memberId), body(orderItemId, 4, "  " + CONTENT + "  ",
                List.of("https://img.example.com/r2.jpg", "https://img.example.com/r1.jpg")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern("/api/members/me/reviews/\\d+")))
                .andExpect(jsonPath("$.orderItemId").value(orderItemId))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.productName").value("독도 토너"))
                .andExpect(jsonPath("$.skuName").value("기본"))
                .andExpect(jsonPath("$.thumbnailUrl").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.content").value(CONTENT))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://img.example.com/r2.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("https://img.example.com/r1.jpg"))
                .andExpect(jsonPath("$.skinType").value("OILY"))
                .andExpect(jsonPath("$.skinTone").value("WARM"))
                .andExpect(jsonPath("$.skinConcerns[0]").value("PORE"))
                .andExpect(jsonPath("$.skinConcerns[1]").value("TROUBLE"))
                .andDo(document("review/write",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("orderItemId").type(JsonFieldType.NUMBER)
                                        .description("리뷰를 쓸 주문상품 ID. 구매확정된 내 주문의 주문상품이어야 한다"),
                                fieldWithPath("rating").type(JsonFieldType.NUMBER).description("별점 (1~5)"),
                                fieldWithPath("content").type(JsonFieldType.STRING)
                                        .description("본문. 앞뒤 공백을 뗀 길이가 10~1,000자여야 한다"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY).optional()
                                        .description("사진 URL 0~5개. 각각 `http://` 또는 `https://`로 시작하고 500자 이하다. 없으면 빈 목록이다")),
                        responseHeaders(headerWithName(HttpHeaders.LOCATION).description("만든 리뷰의 URI")),
                        responseFields(reviewFields())))
                .andReturn().getResponse().getContentAsString();

        Long reviewId = objectMapper.readTree(response).get("reviewId").asLong();
        assertThat(aggregate()).isEqualTo("1/4/4.00");
        assertThat(jdbcTemplate.queryForObject("select count(*) from review where id = ?", Long.class, reviewId))
                .isEqualTo(1);
    }

    @Test
    void 수정하면_평점이_바뀌고_스냅샷은_그대로다() throws Exception {
        memberService.updateSkinProfile(memberId, SkinType.OILY, SkinTone.WARM,
                List.of(SkinConcern.TROUBLE, SkinConcern.PORE));
        Long otherId = fixtures.member("other@mall.com");
        write(otherId, fixtures.orderItem(otherId, productId, "CONFIRMED"), 5);
        Long reviewId = write(memberId, fixtures.orderItem(memberId, productId, "CONFIRMED"), 5);
        memberService.updateSkinProfile(memberId, SkinType.DRY, SkinTone.COOL, List.of(SkinConcern.WRINKLE));

        send(put("/api/members/me/reviews/{reviewId}", reviewId), token(memberId), editBody(2, CONTENT + " 수정",
                List.of("https://img.example.com/e1.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.content").value(CONTENT + " 수정"))
                .andExpect(jsonPath("$.imageUrls.length()").value(1))
                .andExpect(jsonPath("$.skinType").value("OILY"))
                .andExpect(jsonPath("$.skinTone").value("WARM"))
                .andExpect(jsonPath("$.skinConcerns[0]").value("PORE"))
                .andExpect(jsonPath("$.skinConcerns[1]").value("TROUBLE"))
                .andDo(document("review/edit",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("reviewId").description("수정할 리뷰 ID")),
                        requestFields(
                                fieldWithPath("rating").type(JsonFieldType.NUMBER).description("별점 (1~5)"),
                                fieldWithPath("content").type(JsonFieldType.STRING)
                                        .description("본문. 앞뒤 공백을 뗀 길이가 10~1,000자여야 한다"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY).optional()
                                        .description("사진 URL 0~5개. 기존 사진을 모두 이 목록으로 바꾼다. 없거나 비우면 사진을 모두 지운다")),
                        responseFields(reviewFields())));

        assertThat(aggregate()).isEqualTo("2/7/3.50");
        assertThat(jdbcTemplate.queryForObject("select skin_type from review where id = ?", String.class, reviewId))
                .isEqualTo("OILY");

        // 같은 별점으로 고치면 집계는 그대로다.
        send(put("/api/members/me/reviews/{reviewId}", reviewId), token(memberId), editBody(2, CONTENT, List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrls.length()").value(0));
        assertThat(aggregate()).isEqualTo("2/7/3.50");
    }

    @Test
    void 삭제하면_평점이_빠지고_다시_쓸_수_있다() throws Exception {
        Long orderItemId = fixtures.orderItem(memberId, productId, "CONFIRMED");
        Long reviewId = write(memberId, orderItemId, 4);

        mockMvc.perform(delete("/api/members/me/reviews/{reviewId}", reviewId)
                        .header(HttpHeaders.AUTHORIZATION, token(memberId)))
                .andExpect(status().isNoContent())
                .andDo(document("review/delete",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("reviewId").description("삭제할 리뷰 ID"))));

        assertThat(aggregate()).isEqualTo("0/0/null");
        assertThat(jdbcTemplate.queryForObject("select count(*) from review", Long.class)).isZero();

        Long again = write(memberId, orderItemId, 5);
        assertThat(again).isNotEqualTo(reviewId);
        assertThat(aggregate()).isEqualTo("1/5/5.00");
    }

    @Test
    void 구매확정이_아니면_409_REVIEW_NOT_WRITABLE() throws Exception {
        Long orderItemId = fixtures.orderItem(memberId, productId, "DELIVERED");

        send(post("/api/members/me/reviews"), token(memberId), body(orderItemId, 5, CONTENT, List.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_WRITABLE"));

        assertThat(aggregate()).isEqualTo("0/0/null");
    }

    @Test
    void 남의_주문상품이면_404_ORDER_ITEM_NOT_FOUND() throws Exception {
        Long othersItem = fixtures.orderItem(fixtures.member("other@mall.com"), productId, "CONFIRMED");

        send(post("/api/members/me/reviews"), token(memberId), body(othersItem, 5, CONTENT, List.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));
        send(post("/api/members/me/reviews"), token(memberId), body(999_999L, 5, CONTENT, List.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));

        assertThat(aggregate()).isEqualTo("0/0/null");
    }

    @Test
    void 이미_썼으면_409_REVIEW_ALREADY_EXISTS() throws Exception {
        Long orderItemId = fixtures.orderItem(memberId, productId, "CONFIRMED");
        write(memberId, orderItemId, 5);

        send(post("/api/members/me/reviews"), token(memberId), body(orderItemId, 1, CONTENT, List.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        assertThat(aggregate()).isEqualTo("1/5/5.00");
    }

    @Test
    void 남의_리뷰를_고치거나_지우면_404_REVIEW_NOT_FOUND() throws Exception {
        Long reviewId = write(memberId, fixtures.orderItem(memberId, productId, "CONFIRMED"), 5);
        Long otherId = fixtures.member("other@mall.com");

        send(put("/api/members/me/reviews/{reviewId}", reviewId), token(otherId), editBody(1, CONTENT, List.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        mockMvc.perform(delete("/api/members/me/reviews/{reviewId}", reviewId)
                        .header(HttpHeaders.AUTHORIZATION, token(otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        mockMvc.perform(delete("/api/members/me/reviews/{reviewId}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, token(otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(aggregate()).isEqualTo("1/5/5.00");
        assertThat(jdbcTemplate.queryForObject("select rating from review where id = ?", Integer.class, reviewId))
                .isEqualTo(5);
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        Long orderItemId = fixtures.orderItem(memberId, productId, "CONFIRMED");
        String url = "https://img.example.com/r.jpg";
        List<Map<String, Object>> invalid = List.of(
                body(orderItemId, 0, CONTENT, List.of()),
                body(orderItemId, 6, CONTENT, List.of()),
                body(orderItemId, 5, "          ", List.of()),
                body(orderItemId, 5, "  가나다라마바사아자  ", List.of()),
                body(orderItemId, 5, "가".repeat(1_001), List.of()),
                body(orderItemId, 5, CONTENT, List.of(url, url, url, url, url, url)),
                body(orderItemId, 5, CONTENT, List.of("ftp://img.example.com/r.jpg")),
                body(orderItemId, 5, CONTENT, List.of("https://" + "a".repeat(493))),
                body(null, 5, CONTENT, List.of()));
        for (Map<String, Object> request : invalid) {
            send(post("/api/members/me/reviews"), token(memberId), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from review", Long.class)).isZero();
        assertThat(aggregate()).isEqualTo("0/0/null");

        // 수정도 같은 규칙이고, 거절되면 리뷰는 그대로다.
        Long reviewId = write(memberId, orderItemId, 5);
        for (Map<String, Object> request : List.of(editBody(0, CONTENT, List.of()),
                editBody(3, "  가나다라마바사아자  ", List.of()), editBody(3, CONTENT, List.of("ftp://x.example.com/a")))) {
            send(put("/api/members/me/reviews/{reviewId}", reviewId), token(memberId), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(aggregate()).isEqualTo("1/5/5.00");
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(post("/api/members/me/reviews").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/members/me/reviews/{reviewId}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/members/me/reviews/{reviewId}", 1L)).andExpect(status().isUnauthorized());
    }
}
