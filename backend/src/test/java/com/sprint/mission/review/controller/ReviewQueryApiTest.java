package com.sprint.mission.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.review.ReviewFixtures;
import com.sprint.mission.review.dto.ReviewWriteRequest;
import com.sprint.mission.review.service.ReviewService;
import com.sprint.mission.support.ApiTest;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@Import(ReviewFixtures.class)
class ReviewQueryApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");
    private static final String CONTENT = "촉촉하고 순해서 매일 쓰기 좋아요.";

    @Autowired
    ReviewFixtures fixtures;

    @Autowired
    ReviewService reviewService;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Long productId;
    int memberSeq;

    @BeforeEach
    void setUp() {
        productId = fixtures.product("독도 토너");
    }

    /** 새 회원이 구매확정 주문상품에 리뷰를 쓰고 리뷰 id를 돌려준다. 회원의 피부 프로필은 쓰기 전에 정한다. */
    private Long review(int rating, SkinType skinType, List<String> imageUrls) {
        Long memberId = fixtures.member("user" + memberSeq++ + "@mall.com");
        if (skinType != null) {
            memberService.updateSkinProfile(memberId, skinType, SkinTone.WARM,
                    List.of(SkinConcern.TROUBLE, SkinConcern.PORE));
        }
        Long orderItemId = fixtures.orderItem(memberId, productId, "CONFIRMED");
        return reviewService.write(memberId, new ReviewWriteRequest(orderItemId, rating, CONTENT, imageUrls))
                .reviewId();
    }

    private Long review(int rating) {
        return review(rating, null, List.of());
    }

    private ResultActions productReviews(String... params) throws Exception {
        var request = get("/api/products/{productId}/reviews", productId);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page, String field) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get(field).asLong()));
        return ids;
    }

    /** nextCursor가 없을 때까지 size씩 읽어 리뷰 id를 이어 붙인다. */
    private List<Long> readAll(String sort, int size) throws Exception {
        List<Long> all = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = cursor == null ? json(productReviews("sort", sort, "size", "" + size))
                    : json(productReviews("sort", sort, "size", "" + size, "cursor", cursor));
            all.addAll(ids(page, "reviewId"));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
        } while (cursor != null);
        return all;
    }

    private static String cursorOf(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 최신순으로_커서를_넘기며_읽는다() throws Exception {
        Long first = review(5, SkinType.OILY, List.of("https://img.example.com/r2.jpg", "https://img.example.com/r1.jpg"));
        Long second = review(3);
        Long third = review(4);

        JsonNode firstPage = json(productReviews("size", "2")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].reviewId").value(third))
                .andExpect(jsonPath("$.items[1].reviewId").value(second))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andDo(document("review/product-list",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        queryParameters(
                                parameterWithName("sort").optional()
                                        .description("정렬 (`LATEST` 최신순, `RATING_DESC` 별점 높은 순, `RATING_ASC` 별점 낮은 순). 기본 `LATEST`"),
                                parameterWithName("skinType").optional()
                                        .description("리뷰를 쓸 때의 피부 타입으로 거른다 (`DRY`, `OILY`, `COMBINATION`, `SENSITIVE`, `NORMAL`)"),
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("리뷰 목록"),
                                fieldWithPath("items[].reviewId").type(JsonFieldType.NUMBER).description("리뷰 ID"),
                                fieldWithPath("items[].authorName").type(JsonFieldType.STRING)
                                        .description("작성자 이름. 첫 글자만 보이고 나머지는 `**`로 가린다. 탈퇴한 회원은 `탈퇴한 회원`이다"),
                                fieldWithPath("items[].rating").type(JsonFieldType.NUMBER).description("별점 (1~5)"),
                                fieldWithPath("items[].content").type(JsonFieldType.STRING).description("본문"),
                                fieldWithPath("items[].imageUrls").type(JsonFieldType.ARRAY)
                                        .description("사진 URL. 첫 번째가 대표 사진이다"),
                                fieldWithPath("items[].skuName").type(JsonFieldType.STRING)
                                        .description("구매한 옵션명. 주문 시점의 값이다"),
                                fieldWithPath("items[].skinType").type(JsonFieldType.STRING).optional()
                                        .description("작성 시점 작성자의 피부 타입. 없으면 `null`"),
                                fieldWithPath("items[].skinTone").type(JsonFieldType.STRING).optional()
                                        .description("작성 시점 작성자의 피부 톤. 없으면 `null`"),
                                fieldWithPath("items[].skinConcerns").type(JsonFieldType.ARRAY)
                                        .description("작성 시점 작성자의 피부 고민. enum 이름순이다"),
                                fieldWithPath("items[].createdAt").type(JsonFieldType.STRING).description("작성 시각"),
                                fieldWithPath("items[].updatedAt").type(JsonFieldType.STRING).description("마지막 수정 시각"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`")))));

        json(productReviews("size", "2", "cursor", firstPage.get("nextCursor").asText())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reviewId").value(first))
                .andExpect(jsonPath("$.items[0].authorName").value("홍**"))
                .andExpect(jsonPath("$.items[0].rating").value(5))
                .andExpect(jsonPath("$.items[0].content").value(CONTENT))
                .andExpect(jsonPath("$.items[0].imageUrls[0]").value("https://img.example.com/r2.jpg"))
                .andExpect(jsonPath("$.items[0].imageUrls[1]").value("https://img.example.com/r1.jpg"))
                .andExpect(jsonPath("$.items[0].skuName").value("기본"))
                .andExpect(jsonPath("$.items[0].skinType").value("OILY"))
                .andExpect(jsonPath("$.items[0].skinTone").value("WARM"))
                .andExpect(jsonPath("$.items[0].skinConcerns[0]").value("PORE"))
                .andExpect(jsonPath("$.items[0].skinConcerns[1]").value("TROUBLE"))
                .andExpect(jsonPath("$.nextCursor").value(nullValue())));
    }

    @Test
    void 리뷰가_없으면_빈_목록이다() throws Exception {
        productReviews()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 별점_높은_순과_낮은_순은_동률이면_최신순이다() throws Exception {
        Long r1 = review(5);
        Long r2 = review(3);
        Long r3 = review(5);
        Long r4 = review(3);
        Long r5 = review(4);

        assertThat(readAll("RATING_DESC", 2)).containsExactly(r3, r1, r5, r4, r2);
        assertThat(readAll("RATING_ASC", 2)).containsExactly(r4, r2, r5, r3, r1);
        assertThat(readAll("LATEST", 2)).containsExactly(r5, r4, r3, r2, r1);
    }

    @Test
    void 피부타입으로_거른다() throws Exception {
        Long oily = review(5, SkinType.OILY, List.of());
        Long dry = review(4, SkinType.DRY, List.of());
        review(3);
        // 작성자가 프로필을 나중에 바꿔도 리뷰의 스냅샷 기준으로 거른다.
        Long oilyMember = jdbcTemplate.queryForObject("select member_id from review where id = ?", Long.class, oily);
        memberService.updateSkinProfile(oilyMember, SkinType.DRY, SkinTone.COOL, List.of());

        assertThat(ids(json(productReviews("skinType", "OILY")), "reviewId")).containsExactly(oily);
        assertThat(ids(json(productReviews("skinType", "DRY")), "reviewId")).containsExactly(dry);
        assertThat(ids(json(productReviews("skinType", "DRY", "sort", "RATING_ASC", "size", "1")), "reviewId"))
                .containsExactly(dry);
        assertThat(ids(json(productReviews("skinType", "SENSITIVE")), "reviewId")).isEmpty();
    }

    @Test
    void 작성자_이름을_가리고_탈퇴_회원은_탈퇴한_회원이다() throws Exception {
        Long masked = review(5);
        Long withdrawn = review(4);
        Long single = review(3);
        jdbcTemplate.update("update member set name = 'Alice' where id = (select member_id from review where id = ?)",
                masked);
        jdbcTemplate.update("update member set name = '김' where id = (select member_id from review where id = ?)",
                single);
        jdbcTemplate.update("update member set status = 'WITHDRAWN' where id = (select member_id from review where id = ?)",
                withdrawn);

        productReviews()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].authorName").value("김**"))
                .andExpect(jsonPath("$.items[1].authorName").value("탈퇴한 회원"))
                .andExpect(jsonPath("$.items[2].authorName").value("A**"));
    }

    @Test
    void 토큰_없이도_읽을_수_있다() throws Exception {
        review(5);

        productReviews().andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/products/{productId}/reviews", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L, Role.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void 없거나_삭제된_상품은_404() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/reviews", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        review(5);
        jdbcTemplate.update("update product set status = 'DELETED' where id = ?", productId);
        productReviews().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 잘못된_정렬_커서_size는_400() throws Exception {
        review(5);
        review(4);
        String latestCursor = json(productReviews("size", "1")).get("nextCursor").asText();
        String ratingCursor = json(productReviews("sort", "RATING_DESC", "size", "1")).get("nextCursor").asText();

        List<String[]> invalid = List.of(
                new String[] {"sort", "RATING_DESC", "cursor", latestCursor},
                new String[] {"sort", "LATEST", "cursor", ratingCursor},
                new String[] {"sort", "RATING_ASC", "cursor", ratingCursor},
                new String[] {"sort", "RATING_DESC", "cursor", cursorOf("RATING_DESC|6|5")},
                new String[] {"sort", "RATING_DESC", "cursor", cursorOf("RATING_DESC|0|5")},
                new String[] {"sort", "RATING_DESC", "cursor", cursorOf("RATING_DESC|x|5")},
                new String[] {"sort", "RATING_DESC", "cursor", cursorOf("RATING_DESC||5")},
                new String[] {"sort", "RATING_DESC", "cursor", cursorOf("RATING_DESC|3|0")},
                new String[] {"cursor", cursorOf("LATEST|3|5")},
                new String[] {"cursor", cursorOf("LATEST||99999999999999999999")},
                new String[] {"cursor", cursorOf("LATEST||5|1")},
                new String[] {"cursor", "garbage!!"},
                new String[] {"cursor", "x".repeat(101)},
                new String[] {"size", "0"},
                new String[] {"size", "51"},
                new String[] {"size", "abc"},
                new String[] {"sort", "FOO"},
                new String[] {"skinType", "FOO"});
        for (String[] params : invalid) {
            productReviews(params).andExpect(status().isBadRequest());
        }
        productReviews("cursor", cursorOf("LATEST|3|5")).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 목록_쿼리_수는_리뷰_수와_상관없다() throws Exception {
        List<String> images = List.of("https://img.example.com/r1.jpg", "https://img.example.com/r2.jpg");
        review(5, SkinType.OILY, images);
        review(4, SkinType.DRY, images);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        productReviews().andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        long withTwo = statistics.getPrepareStatementCount();

        for (int i = 2; i < 10; i++) {
            review(3, SkinType.NORMAL, images);
        }
        statistics.clear();
        productReviews().andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10));
        long withTen = statistics.getPrepareStatementCount();

        assertThat(withTwo).isPositive();
        assertThat(withTen).isEqualTo(withTwo);
    }

    @Test
    void 내_리뷰를_최신순으로_읽는다() throws Exception {
        Long memberId = fixtures.member("me@mall.com");
        Long first = reviewService.write(memberId, new ReviewWriteRequest(
                fixtures.orderItem(memberId, productId, "CONFIRMED"), 5, CONTENT,
                List.of("https://img.example.com/r1.jpg"))).reviewId();
        Long second = reviewService.write(memberId, new ReviewWriteRequest(
                fixtures.orderItem(memberId, productId, "CONFIRMED"), 2, CONTENT, List.of())).reviewId();
        review(4);
        String token = bearer(memberId, Role.USER);

        JsonNode firstPage = json(mockMvc.perform(get("/api/members/me/reviews").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reviewId").value(second))
                .andExpect(jsonPath("$.items[0].productName").value("독도 토너"))
                .andExpect(jsonPath("$.items[0].skuName").value("기본"))
                .andExpect(jsonPath("$.items[0].rating").value(2))
                .andExpect(jsonPath("$.nextCursor").value(String.valueOf(second)))
                .andDo(document("review/my-list",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("내 리뷰. 최신순이다"),
                                fieldWithPath("items[].reviewId").type(JsonFieldType.NUMBER).description("리뷰 ID"),
                                fieldWithPath("items[].orderItemId").type(JsonFieldType.NUMBER)
                                        .description("리뷰를 쓴 주문상품 ID"),
                                fieldWithPath("items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].productName").type(JsonFieldType.STRING)
                                        .description("주문 시점의 상품명"),
                                fieldWithPath("items[].skuName").type(JsonFieldType.STRING)
                                        .description("주문 시점의 옵션명"),
                                fieldWithPath("items[].thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("주문 시점의 썸네일 URL. 없으면 `null`"),
                                fieldWithPath("items[].rating").type(JsonFieldType.NUMBER).description("별점 (1~5)"),
                                fieldWithPath("items[].content").type(JsonFieldType.STRING).description("본문"),
                                fieldWithPath("items[].imageUrls").type(JsonFieldType.ARRAY)
                                        .description("사진 URL. 첫 번째가 대표 사진이다"),
                                fieldWithPath("items[].skinType").type(JsonFieldType.STRING).optional()
                                        .description("작성 시점 내 피부 타입. 없으면 `null`"),
                                fieldWithPath("items[].skinTone").type(JsonFieldType.STRING).optional()
                                        .description("작성 시점 내 피부 톤. 없으면 `null`"),
                                fieldWithPath("items[].skinConcerns").type(JsonFieldType.ARRAY)
                                        .description("작성 시점 내 피부 고민. enum 이름순이다"),
                                fieldWithPath("items[].createdAt").type(JsonFieldType.STRING).description("작성 시각"),
                                fieldWithPath("items[].updatedAt").type(JsonFieldType.STRING)
                                        .description("마지막 수정 시각"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`")))));

        json(mockMvc.perform(get("/api/members/me/reviews").param("cursor", firstPage.get("nextCursor").asText())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reviewId").value(first))
                .andExpect(jsonPath("$.items[0].imageUrls[0]").value("https://img.example.com/r1.jpg"))
                .andExpect(jsonPath("$.nextCursor").value(nullValue())));

        mockMvc.perform(get("/api/members/me/reviews").param("cursor", "abc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/members/me/reviews").param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/members/me/reviews")).andExpect(status().isUnauthorized());
    }

    @Test
    void 작성_가능한_상품은_확정됐고_리뷰가_없는_주문상품뿐이다() throws Exception {
        Long memberId = fixtures.member("me@mall.com");
        Long reviewed = fixtures.orderItem(memberId, productId, "CONFIRMED");
        Long writable = fixtures.orderItem(memberId, productId, "CONFIRMED");
        fixtures.orderItem(memberId, productId, "DELIVERED");
        reviewService.write(memberId, new ReviewWriteRequest(reviewed, 5, CONTENT, List.of()));
        review(4);
        String token = bearer(memberId, Role.USER);

        mockMvc.perform(get("/api/members/me/reviews/writable").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orderItemId").value(writable))
                .andExpect(jsonPath("$.items[0].orderNumber").isString())
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].productName").value("독도 토너"))
                .andExpect(jsonPath("$.items[0].skuName").value("기본"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.items[0].confirmedAt").isString())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andDo(document("review/writable",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY)
                                        .description("리뷰를 쓸 수 있는 주문상품. 주문상품 ID 내림차순이다"),
                                fieldWithPath("items[].orderItemId").type(JsonFieldType.NUMBER)
                                        .description("리뷰를 쓸 때 보내는 주문상품 ID"),
                                fieldWithPath("items[].orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                                fieldWithPath("items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].productName").type(JsonFieldType.STRING)
                                        .description("주문 시점의 상품명"),
                                fieldWithPath("items[].skuName").type(JsonFieldType.STRING)
                                        .description("주문 시점의 옵션명"),
                                fieldWithPath("items[].thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("주문 시점의 썸네일 URL. 없으면 `null`"),
                                fieldWithPath("items[].confirmedAt").type(JsonFieldType.STRING)
                                        .description("구매확정 시각"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`"))));

        // 리뷰를 지우면 다시 작성 가능 목록에 나온다. 커서는 주문상품 id다.
        Long reviewId = jdbcTemplate.queryForObject("select id from review where order_item_id = ?", Long.class,
                reviewed);
        jdbcTemplate.update("delete from review where id = ?", reviewId);
        JsonNode page = json(mockMvc.perform(get("/api/members/me/reviews/writable").param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, token)));
        assertThat(ids(page, "orderItemId")).containsExactly(writable);
        assertThat(page.get("nextCursor").asText()).isEqualTo(String.valueOf(writable));
        assertThat(ids(json(mockMvc.perform(get("/api/members/me/reviews/writable")
                .param("cursor", page.get("nextCursor").asText()).header(HttpHeaders.AUTHORIZATION, token))),
                "orderItemId")).containsExactly(reviewed);

        mockMvc.perform(get("/api/members/me/reviews/writable").param("cursor", "0")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/members/me/reviews/writable")).andExpect(status().isUnauthorized());
    }
}
