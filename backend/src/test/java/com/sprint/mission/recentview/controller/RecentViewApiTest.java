package com.sprint.mission.recentview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.sprint.mission.recentview.service.RecentViewService;
import com.sprint.mission.support.ApiTest;
import com.sprint.mission.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class RecentViewApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    /** 기록 사이에 시간을 흘려 viewed_at 동률로 순서가 흔들리지 않게 한다. */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    MutableClock clock;

    @Autowired
    MemberService memberService;

    @Autowired
    RecentViewService recentViewService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

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

    private ResultActions view(Long productId) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        return mockMvc.perform(post("/api/members/me/recent-views")
                .header(HttpHeaders.AUTHORIZATION, token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + "}"));
    }

    private ResultActions list() throws Exception {
        return mockMvc.perform(get("/api/members/me/recent-views").header(HttpHeaders.AUTHORIZATION, token()));
    }

    private int rows() {
        return jdbcTemplate.queryForObject("select count(*) from recent_view", Integer.class);
    }

    @Test
    void 본_상품이_최신순으로_쌓인다() throws Exception {
        Long a = register("토너 A", 10_000);
        Long b = register("토너 B", 18_000);
        Long c = register("토너 C", 12_900);
        Instant now = Instant.now();
        adminProductService.startSale(c, new SaleRequest(10, now.minusSeconds(3_600), now.plusSeconds(3_600)));

        view(a).andExpect(status().isNoContent());
        view(b).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(1));
        mockMvc.perform(post("/api/members/me/recent-views")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + c + "}"))
                .andExpect(status().isNoContent())
                .andDo(document("recent-view/record",
                        requestHeaders(AUTHORIZATION),
                        requestFields(fieldWithPath("productId").type(JsonFieldType.NUMBER)
                                .description("본 상품 ID. 판매 중인 상품만 기록할 수 있다"))));

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].product.id").value(contains(c.intValue(), b.intValue(), a.intValue())))
                .andExpect(jsonPath("$[0].product.name").value("토너 C"))
                .andExpect(jsonPath("$[0].product.brandName").value("라운드랩"))
                .andExpect(jsonPath("$[0].product.salePrice").value(11_610))
                .andExpect(jsonPath("$[0].product.saleRate").value(10))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].viewedAt").value(notNullValue()))
                .andDo(document("recent-view/list",
                        requestHeaders(AUTHORIZATION),
                        responseFields(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY)
                                        .description("최근 본 상품 목록. 최근에 본 순서이고 최대 50개다"),
                                fieldWithPath("[].product").type(JsonFieldType.OBJECT).description("상품 카드"),
                                fieldWithPath("[].product.id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("[].product.name").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("[].product.brandName").type(JsonFieldType.STRING)
                                        .description("브랜드 이름"),
                                fieldWithPath("[].product.thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("대표 이미지 URL. 상품에 이미지가 없으면 null이다"),
                                fieldWithPath("[].product.price").type(JsonFieldType.NUMBER)
                                        .description("정가 (원). 옵션 중 가장 싼 정가다"),
                                fieldWithPath("[].product.salePrice").type(JsonFieldType.NUMBER)
                                        .description("현재 판매가 (원)"),
                                fieldWithPath("[].product.saleRate").type(JsonFieldType.NUMBER).optional()
                                        .description("진행 중인 세일의 할인율 (%). 진행 중인 세일이 없으면 `null`"),
                                fieldWithPath("[].product.soldOut").type(JsonFieldType.BOOLEAN)
                                        .description("품절 여부. 모든 옵션이 품절이면 `true`다"),
                                fieldWithPath("[].available").type(JsonFieldType.BOOLEAN)
                                        .description("지금 구매할 수 있는 상품인지 여부. 판매 중지된 상품은 `false`다"),
                                fieldWithPath("[].viewedAt").type(JsonFieldType.STRING)
                                        .description("마지막으로 본 시각 (ISO-8601)"))));
    }

    @Test
    void 같은_상품을_다시_보면_맨_앞으로_온다() throws Exception {
        Long a = register("토너 A", 10_000);
        Long b = register("토너 B", 10_000);

        view(a);
        view(b);
        view(a);

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$[*].product.id").value(contains(a.intValue(), b.intValue())));
        assertThat(rows()).isEqualTo(2);
    }

    @Test
    void 오십_한_번째를_기록하면_가장_오래된_것이_빠진다() throws Exception {
        List<Long> viewed = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            Long id = register("토너 " + i, 10_000L + i);
            clock.advance(Duration.ofSeconds(1));
            recentViewService.record(memberId, id);
            viewed.add(id);
        }

        assertThat(rows()).isEqualTo(50);
        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[0].product.id").value(viewed.get(50)))
                .andExpect(jsonPath("$[49].product.id").value(viewed.get(1)));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from recent_view where product_id = ?", Integer.class, viewed.get(0))).isZero();
    }

    @Test
    void 오십_개일_때_가장_오래된_것을_다시_보면_오십_개_그대로_맨_앞이다() throws Exception {
        List<Long> viewed = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Long id = register("토너 " + i, 10_000L + i);
            clock.advance(Duration.ofSeconds(1));
            recentViewService.record(memberId, id);
            viewed.add(id);
        }

        view(viewed.get(0)).andExpect(status().isNoContent());

        assertThat(rows()).isEqualTo(50);
        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[0].product.id").value(viewed.get(0)))
                .andExpect(jsonPath("$[49].product.id").value(viewed.get(1)));
    }

    @Test
    void 하나를_지우거나_전부_지운다() throws Exception {
        Long a = register("토너 A", 10_000);
        Long b = register("토너 B", 10_000);
        Long c = register("토너 C", 10_000);
        view(a);
        view(b);
        view(c);

        mockMvc.perform(delete("/api/members/me/recent-views/{productId}", b)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andDo(document("recent-view/remove",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("productId").description("목록에서 지울 상품 ID"))));
        list().andExpect(jsonPath("$[*].product.id").value(contains(c.intValue(), a.intValue())));
        mockMvc.perform(delete("/api/members/me/recent-views/{productId}", b)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/members/me/recent-views/{productId}", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/members/me/recent-views").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andDo(document("recent-view/clear", requestHeaders(AUTHORIZATION)));
        list().andExpect(status().isOk()).andExpect(jsonPath("$").value(empty()));
        assertThat(rows()).isZero();
    }

    @Test
    void 판매중지_상품은_기록할_수_없다() throws Exception {
        Long id = register("토너", 10_000);
        adminProductService.changeStatus(id, new ProductStatusRequest(ProductStatus.STOPPED));

        view(id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        view(999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertThat(rows()).isZero();
    }

    @Test
    void 본_뒤_판매중지되면_available_false로_보인다() throws Exception {
        Long id = register("토너", 10_000);
        view(id);
        adminProductService.changeStatus(id, new ProductStatusRequest(ProductStatus.STOPPED));

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].product.id").value(id))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void 본_뒤_삭제되면_빠진다() throws Exception {
        Long kept = register("남는 토너", 10_000);
        Long removed = register("지워질 토너", 10_000);
        view(kept);
        view(removed);
        adminProductService.changeStatus(removed, new ProductStatusRequest(ProductStatus.DELETED));

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$[*].product.id").value(contains(kept.intValue())));
    }

    @Test
    void 다른_회원의_기록은_보이지_않는다() throws Exception {
        Long id = register("토너", 10_000);
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01099998888");
        recentViewService.record(other, id);

        list().andExpect(jsonPath("$").value(empty()));
        mockMvc.perform(delete("/api/members/me/recent-views").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent());
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        for (String body : List.of("{}", "{\"productId\":null}", "{\"productId\":\"abc\"}", "")) {
            mockMvc.perform(post("/api/members/me/recent-views")
                            .header(HttpHeaders.AUTHORIZATION, token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(get("/api/members/me/recent-views")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/members/me/recent-views").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/members/me/recent-views/{productId}", 1)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/members/me/recent-views")).andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴한_회원의_토큰이면_404() throws Exception {
        Long id = register("토너", 10_000);
        memberService.withdraw(memberId, "password1");

        view(id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        list().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        mockMvc.perform(delete("/api/members/me/recent-views/{productId}", id)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/members/me/recent-views").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNotFound());
    }
}
