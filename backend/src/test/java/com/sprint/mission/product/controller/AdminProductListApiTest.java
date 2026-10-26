package com.sprint.mission.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
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

class AdminProductListApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Long roundLab;
    Long anua;
    Long tonerId;

    @BeforeEach
    void setUp() {
        roundLab = brandService.create(new BrandRequest("라운드랩", null)).id();
        anua = brandService.create(new BrandRequest("아누아", null)).id();
        tonerId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private Long register(Long brandId, String name, int... stocks) {
        List<SkuRequest> skus = new java.util.ArrayList<>();
        for (int i = 0; i < stocks.length; i++) {
            skus.add(new SkuRequest("옵션" + i, 0, stocks[i]));
        }
        return adminProductService.create(new ProductCreateRequest(brandId, tonerId, name, null, "설명이다.", null,
                10_000, List.of("https://img.example.com/" + name + ".jpg", "https://img.example.com/sub.jpg"), skus));
    }

    private Long register(String name, int... stocks) {
        return register(roundLab, name, stocks);
    }

    private void sale(Long productId, long startsInSeconds, long endsInSeconds) {
        Instant now = Instant.now();
        adminProductService.startSale(productId,
                new SaleRequest(20, now.plusSeconds(startsInSeconds), now.plusSeconds(endsInSeconds)));
    }

    private void changeStatus(Long productId, ProductStatus status) {
        adminProductService.changeStatus(productId, new ProductStatusRequest(status));
    }

    private ResultActions list(String token, String... params) throws Exception {
        var request = get("/api/admin/products").header(HttpHeaders.AUTHORIZATION, token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private JsonNode parse(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void 판매_중지와_삭제_상품도_최근_순으로_보인다() throws Exception {
        Long onSale = register("토너", 10, 3);
        sale(onSale, -3_600, 3_600);
        Long stopped = register("에센스", 7);
        sale(stopped, 3_600, 7_200);
        changeStatus(stopped, ProductStatus.STOPPED);
        Long deleted = register("크림", 0);
        changeStatus(deleted, ProductStatus.DELETED);

        list(admin(), "size", "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(
                        deleted.intValue(), stopped.intValue(), onSale.intValue())))
                .andExpect(jsonPath("$.items[0].status").value("DELETED"))
                .andExpect(jsonPath("$.items[0].saleRate").doesNotExist())
                .andExpect(jsonPath("$.items[0].saleActive").value(false))
                .andExpect(jsonPath("$.items[0].skuCount").value(1))
                .andExpect(jsonPath("$.items[0].minStock").value(0))
                .andExpect(jsonPath("$.items[1].status").value("STOPPED"))
                // 아직 시작하지 않은 세일은 설정된 세일률만 보이고 진행 중은 아니다.
                .andExpect(jsonPath("$.items[1].saleRate").value(20))
                .andExpect(jsonPath("$.items[1].saleActive").value(false))
                .andExpect(jsonPath("$.items[2].name").value("토너"))
                .andExpect(jsonPath("$.items[2].brandName").value("라운드랩"))
                .andExpect(jsonPath("$.items[2].thumbnailUrl").value("https://img.example.com/토너.jpg"))
                .andExpect(jsonPath("$.items[2].status").value("ON_SALE"))
                .andExpect(jsonPath("$.items[2].basePrice").value(10_000))
                .andExpect(jsonPath("$.items[2].saleRate").value(20))
                .andExpect(jsonPath("$.items[2].saleActive").value(true))
                .andExpect(jsonPath("$.items[2].skuCount").value(2))
                .andExpect(jsonPath("$.items[2].minStock").value(3))
                .andExpect(jsonPath("$.items[2].createdAt").isString())
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andDo(document("admin-product/list",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("keyword").optional()
                                        .description("검색어. 앞뒤 공백을 지운 1~50자이며 상품명과 브랜드명에서 대소문자 구분 없이 찾는다"),
                                parameterWithName("status").optional()
                                        .description("상품 상태로 거른다 (`ON_SALE`, `STOPPED`, `DELETED`). 없으면 모든 상태다"),
                                parameterWithName("brandId").optional().description("브랜드 ID로 거른다"),
                                parameterWithName("lowStock").optional()
                                        .description("`true`면 판매 중이고 재고가 5개 이하인 옵션이 하나라도 있는 상품만 보인다. "
                                                + "없거나 `false`면 거르지 않는다"),
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY)
                                        .description("상품. 최근 등록한 상품이 먼저다"),
                                fieldWithPath("items[].id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].name").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("items[].brandName").type(JsonFieldType.STRING).description("브랜드명"),
                                fieldWithPath("items[].thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("대표 이미지 URL. 이미지가 없으면 `null`이다"),
                                fieldWithPath("items[].status").type(JsonFieldType.STRING)
                                        .description("상품 상태 (`ON_SALE`, `STOPPED`, `DELETED`)"),
                                fieldWithPath("items[].basePrice").type(JsonFieldType.NUMBER)
                                        .description("정가 (원)"),
                                fieldWithPath("items[].saleRate").type(JsonFieldType.NUMBER).optional()
                                        .description("설정된 세일률 (%). 세일을 걸지 않았으면 `null`이다. "
                                                + "기간이 지났거나 아직 시작하지 않은 세일도 값이 남는다"),
                                fieldWithPath("items[].saleActive").type(JsonFieldType.BOOLEAN)
                                        .description("세일이 지금 진행 중인지 여부"),
                                fieldWithPath("items[].skuCount").type(JsonFieldType.NUMBER)
                                        .description("옵션(SKU) 수"),
                                fieldWithPath("items[].minStock").type(JsonFieldType.NUMBER)
                                        .description("옵션 중 가장 적은 재고 수량"),
                                fieldWithPath("items[].createdAt").type(JsonFieldType.STRING).description("등록 시각"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`"))));
    }

    @Test
    void 상태와_브랜드와_키워드로_거른다() throws Exception {
        Long toner = register("1025 독도 토너", 10);
        Long stopped = register("진정 에센스", 10);
        changeStatus(stopped, ProductStatus.STOPPED);
        Long anuaToner = register(anua, "어성초 토너", 10);
        Long percent = register("100% 순수 앰플", 10);

        list(admin(), "status", "STOPPED").andExpect(jsonPath("$.items[*].id").value(contains(stopped.intValue())));
        list(admin(), "status", "ON_SALE").andExpect(jsonPath("$.items.length()").value(3));
        list(admin(), "status", "DELETED").andExpect(jsonPath("$.items.length()").value(0));
        list(admin(), "brandId", anua.toString())
                .andExpect(jsonPath("$.items[*].id").value(contains(anuaToner.intValue())));
        // 상품명과 브랜드명 모두 검색 대상이고, 대소문자는 구분하지 않는다.
        list(admin(), "keyword", "토너")
                .andExpect(jsonPath("$.items[*].id").value(contains(anuaToner.intValue(), toner.intValue())));
        list(admin(), "keyword", "아누아")
                .andExpect(jsonPath("$.items[*].id").value(contains(anuaToner.intValue())));
        // %는 와일드카드가 아니라 글자 그대로 찾는다.
        list(admin(), "keyword", "%").andExpect(jsonPath("$.items[*].id").value(contains(percent.intValue())));
        list(admin(), "keyword", " 토너 ", "brandId", roundLab.toString(), "status", "ON_SALE")
                .andExpect(jsonPath("$.items[*].id").value(contains(toner.intValue())));
        list(admin(), "keyword", "없는상품").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 재고_부족_필터는_판매_중_상품만() throws Exception {
        Long low = register("재고 3", 3);
        Long boundary = register("옵션 중 하나가 5", 10, 5);
        Long soldOut = register("품절", 0);
        register("재고 6", 6);
        Long stopped = register("중지 재고 3", 3);
        changeStatus(stopped, ProductStatus.STOPPED);
        Long deleted = register("삭제 재고 3", 3);
        changeStatus(deleted, ProductStatus.DELETED);

        list(admin(), "lowStock", "true")
                .andExpect(jsonPath("$.items[*].id").value(contains(
                        soldOut.intValue(), boundary.intValue(), low.intValue())));
        // false나 생략이면 거르지 않는다.
        list(admin(), "lowStock", "false").andExpect(jsonPath("$.items.length()").value(6));
        // 상태 필터와 같이 쓰면 둘 다 만족해야 하므로 STOPPED는 비어 있다.
        list(admin(), "lowStock", "true", "status", "STOPPED").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 다음_페이지를_이어_읽는다() throws Exception {
        Long first = register("a", 1);
        Long second = register("b", 1);
        Long third = register("c", 1);

        JsonNode page1 = parse(list(admin(), "size", "2")
                .andExpect(jsonPath("$.items[*].id").value(contains(third.intValue(), second.intValue())))
                .andExpect(jsonPath("$.nextCursor").value(second.toString())));
        list(admin(), "size", "2", "cursor", page1.get("nextCursor").asText())
                .andExpect(jsonPath("$.items[*].id").value(contains(first.intValue())))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        // 정확히 size개로 끝나면 다음 커서가 없다.
        list(admin(), "size", "3").andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void 쿼리_수는_페이지_크기와_상관없다() throws Exception {
        register("a", 1);
        register("b", 1);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        list(admin(), "lowStock", "true").andExpect(jsonPath("$.items.length()").value(2));
        long withTwo = statistics.getPrepareStatementCount();

        for (int i = 0; i < 8; i++) {
            register("p" + i, 1, 2);
        }
        statistics.clear();
        list(admin(), "lowStock", "true").andExpect(jsonPath("$.items.length()").value(10));
        long withTen = statistics.getPrepareStatementCount();

        assertThat(withTwo).isPositive();
        assertThat(withTen).isEqualTo(withTwo);
    }

    @Test
    void 잘못된_커서나_size면_400() throws Exception {
        list(admin(), "cursor", "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list(admin(), "cursor", "0").andExpect(status().isBadRequest());
        list(admin(), "cursor", "-1").andExpect(status().isBadRequest());
        list(admin(), "cursor", "9999999999999999999").andExpect(status().isBadRequest());
        list(admin(), "cursor", "1".repeat(21)).andExpect(status().isBadRequest());
        list(admin(), "size", "0").andExpect(status().isBadRequest());
        list(admin(), "size", "51").andExpect(status().isBadRequest());
        list(admin(), "status", "SHIPPED").andExpect(status().isBadRequest());
        list(admin(), "keyword", " ").andExpect(status().isBadRequest());
        list(admin(), "keyword", "a".repeat(51)).andExpect(status().isBadRequest());
        list(admin(), "keyword", "a\u0001b").andExpect(status().isBadRequest());
    }

    @Test
    void 일반_회원이면_403() throws Exception {
        list(bearer(2L, Role.USER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
    }
}
