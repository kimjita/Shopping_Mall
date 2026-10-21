package com.sprint.mission.wishlist.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import com.sprint.mission.wishlist.service.WishlistService;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class WishlistApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    WishlistService wishlistService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Long memberId;
    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private String token() {
        return bearer(memberId, Role.USER);
    }

    private Long register(String name, long basePrice) {
        return adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, basePrice, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
    }

    private ResultActions wish(Long productId) throws Exception {
        return mockMvc.perform(put("/api/members/me/wishlist/{productId}", productId)
                .header(HttpHeaders.AUTHORIZATION, token()));
    }

    private ResultActions list(String... params) throws Exception {
        var request = get("/api/members/me/wishlist").header(HttpHeaders.AUTHORIZATION, token());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions ids(String productIds) throws Exception {
        return mockMvc.perform(get("/api/members/me/wishlist/ids").param("productIds", productIds)
                .header(HttpHeaders.AUTHORIZATION, token()));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private int wishlistRows() {
        return jdbcTemplate.queryForObject("select count(*) from wishlist", Integer.class);
    }

    @Test
    void 찜하고_목록에서_본다() throws Exception {
        Long first = register("1025 독도 토너", 12_900);
        Long second = register("어성초 진정 토너", 18_000);
        Instant now = Instant.now();
        adminProductService.startSale(second, new SaleRequest(10, now.minusSeconds(3_600), now.plusSeconds(3_600)));

        mockMvc.perform(put("/api/members/me/wishlist/{productId}", first)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andDo(document("wishlist/add",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("productId").description("찜할 상품 ID"))));
        wish(second).andExpect(status().isNoContent());

        list("size", "20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].product.id").value(contains(second.intValue(), first.intValue())))
                .andExpect(jsonPath("$.items[0].product.name").value("어성초 진정 토너"))
                .andExpect(jsonPath("$.items[0].product.brandName").value("라운드랩"))
                .andExpect(jsonPath("$.items[0].product.salePrice").value(16_200))
                .andExpect(jsonPath("$.items[0].product.saleRate").value(10))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[0].wishedAt").value(notNullValue()))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andDo(document("wishlist/list",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("cursor").optional()
                                        .description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional()
                                        .description("한 번에 가져올 개수. 기본 20이고 1~50이다")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("찜한 상품 목록. 최근에 찜한 순서다"),
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
                                        .description("현재 판매가 (원)"),
                                fieldWithPath("items[].product.saleRate").type(JsonFieldType.NUMBER).optional()
                                        .description("진행 중인 세일의 할인율 (%). 진행 중인 세일이 없으면 `null`"),
                                fieldWithPath("items[].product.soldOut").type(JsonFieldType.BOOLEAN)
                                        .description("품절 여부. 모든 옵션이 품절이면 `true`다"),
                                fieldWithPath("items[].product.ratingAvg").type(JsonFieldType.NUMBER).optional()
                                        .description("평균 평점. 소수 둘째 자리까지 반올림한 값이다. 리뷰가 없으면 `null`"),
                                fieldWithPath("items[].product.reviewCount").type(JsonFieldType.NUMBER)
                                        .description("리뷰 수"),
                                fieldWithPath("items[].available").type(JsonFieldType.BOOLEAN)
                                        .description("지금 구매할 수 있는 상품인지 여부. 판매 중지된 상품은 `false`다"),
                                fieldWithPath("items[].wishedAt").type(JsonFieldType.STRING)
                                        .description("찜한 시각 (ISO-8601)"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지를 읽는 커서. 마지막 페이지면 `null`"))));
    }

    @Test
    void 같은_상품을_두_번_찜해도_하나다() throws Exception {
        Long id = register("토너", 10_000);

        wish(id).andExpect(status().isNoContent());
        wish(id).andExpect(status().isNoContent());

        assertThat(wishlistRows()).isEqualTo(1);
        list().andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void 찜을_취소한다() throws Exception {
        Long id = register("토너", 10_000);
        wish(id);

        mockMvc.perform(delete("/api/members/me/wishlist/{productId}", id)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andDo(document("wishlist/remove",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("productId").description("찜을 취소할 상품 ID"))));

        assertThat(wishlistRows()).isZero();
        list().andExpect(jsonPath("$.items").value(empty()));
        mockMvc.perform(delete("/api/members/me/wishlist/{productId}", id)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/members/me/wishlist/{productId}", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 판매중지_상품은_찜할_수_없다() throws Exception {
        Long id = register("토너", 10_000);
        adminProductService.changeStatus(id, new ProductStatusRequest(ProductStatus.STOPPED));

        wish(id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        wish(999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertThat(wishlistRows()).isZero();
    }

    @Test
    void 찜한_뒤_판매중지되면_available_false로_보인다() throws Exception {
        Long id = register("토너", 10_000);
        wish(id);
        adminProductService.changeStatus(id, new ProductStatusRequest(ProductStatus.STOPPED));

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].product.id").value(id))
                .andExpect(jsonPath("$.items[0].available").value(false));
    }

    @Test
    void 찜한_뒤_삭제된_상품은_목록에서_빠진다() throws Exception {
        Long kept = register("남는 토너", 10_000);
        Long removed = register("지워질 토너", 10_000);
        wish(kept);
        wish(removed);
        adminProductService.changeStatus(removed, new ProductStatusRequest(ProductStatus.DELETED));

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].product.id").value(contains(kept.intValue())));
    }

    @Test
    void 커서로_끝까지_읽는다() throws Exception {
        List<Long> wished = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Long id = register("토너 " + i, 10_000L + i);
            wish(id);
            wished.add(id);
        }

        List<Long> read = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            ResultActions result = cursor == null ? list("size", "2") : list("size", "2", "cursor", cursor);
            JsonNode page = json(result);
            page.get("items").forEach(item -> read.add(item.get("product").get("id").asLong()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(read).containsExactly(wished.reversed().toArray(Long[]::new));
    }

    @Test
    void 찜_여부를_한_번에_확인한다() throws Exception {
        Long a = register("토너 1", 10_000);
        Long b = register("토너 2", 10_000);
        Long c = register("토너 3", 10_000);
        wish(c);
        wish(a);

        mockMvc.perform(get("/api/members/me/wishlist/ids")
                        .param("productIds", a + "," + b + "," + c)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productIds").value(contains(a.intValue(), c.intValue())))
                .andDo(document("wishlist/ids",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(parameterWithName("productIds")
                                .description("확인할 상품 ID 목록. 1~100개이며 `1,2,3`처럼 콤마로 구분하거나 파라미터를 반복한다")),
                        responseFields(fieldWithPath("productIds").type(JsonFieldType.ARRAY)
                                .description("찜한 상품 ID. 요청한 순서를 유지하고 찜하지 않은 상품은 빠진다"))));
    }

    @Test
    void 다른_회원의_찜은_보이지_않는다() throws Exception {
        Long id = register("토너", 10_000);
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01099998888");
        wishlistService.add(other, id);

        list().andExpect(jsonPath("$.items").value(empty()));
        ids(id.toString()).andExpect(jsonPath("$.productIds").value(empty()));
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        list("size", "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list("size", "51").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list("cursor", "abc").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list("cursor", "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list("cursor", "-3").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        String tooMany = String.join(",", IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).toList());
        ids(tooMany).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        ids(" ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        ids("1,,2").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/members/me/wishlist/ids").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 목록_조회의_쿼리_수는_페이지_크기와_무관하다() throws Exception {
        for (int i = 1; i <= 21; i++) {
            wishlistService.add(memberId, register("토너 " + i, 10_000L + i));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long[] counts = new long[2];
        int[] sizes = {2, 20};
        for (int i = 0; i < sizes.length; i++) {
            statistics.clear();
            list("size", String.valueOf(sizes[i]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(sizes[i]));
            counts[i] = statistics.getPrepareStatementCount();
        }

        assertThat(counts[0]).isEqualTo(counts[1]);
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(get("/api/members/me/wishlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/members/me/wishlist/{productId}", 1)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/members/me/wishlist/{productId}", 1)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/me/wishlist/ids").param("productIds", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴한_회원의_토큰이면_404() throws Exception {
        Long id = register("토너", 10_000);
        memberService.withdraw(memberId, "password1");

        wish(id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        list().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        ids(id.toString()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        mockMvc.perform(delete("/api/members/me/wishlist/{productId}", id)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNotFound());
    }
}
