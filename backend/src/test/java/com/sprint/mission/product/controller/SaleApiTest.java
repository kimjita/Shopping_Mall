package com.sprint.mission.product.controller;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.product.repository.SkuRepository;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class SaleApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    BrandService brandService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    SkuRepository skuRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long tonerId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Product product = productRepository.save(Product.create(brandId, tonerId, "독도 토너", null,
                "피부결을 정돈하는 토너다.", null, 12_900, List.of("https://img.example.com/main.jpg")));
        skuRepository.save(Sku.create(product.getId(), "150ml", 0, 10));
        skuRepository.save(Sku.create(product.getId(), "300ml", 2_000, 10));
        productId = product.getId();
    }

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private static String saleBody(int rate, Instant startsAt, Instant endsAt) {
        return """
                {"rate":%d,"startsAt":"%s","endsAt":"%s"}""".formatted(rate, startsAt, endsAt);
    }

    private ResultActions putSale(Long id, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/products/{productId}/sale", id)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 세일을_걸면_공개_상세에_세일가가_나온다() throws Exception {
        Instant now = Instant.now();
        Instant startsAt = now.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = now.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        putSale(productId, saleBody(15, startsAt, endsAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale.rate").value(15))
                .andExpect(jsonPath("$.sale.active").value(true))
                .andDo(document("admin-product/start-sale",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("rate").type(JsonFieldType.NUMBER).description("할인율 (%, 1~90)"),
                                fieldWithPath("startsAt").type(JsonFieldType.STRING)
                                        .description("세일 시작 시각 (ISO-8601, 이 시각 정각부터 세일이다)"),
                                fieldWithPath("endsAt").type(JsonFieldType.STRING)
                                        .description("세일 종료 시각 (ISO-8601, 시작보다 늦어야 하고 이 시각 정각에는 세일이 아니다)")),
                        relaxedResponseFields(
                                fieldWithPath("sale").type(JsonFieldType.OBJECT).description("걸린 세일"),
                                fieldWithPath("sale.rate").type(JsonFieldType.NUMBER).description("할인율 (%)"),
                                fieldWithPath("sale.startsAt").type(JsonFieldType.STRING).description("세일 시작 시각"),
                                fieldWithPath("sale.endsAt").type(JsonFieldType.STRING).description("세일 종료 시각"),
                                fieldWithPath("sale.active").type(JsonFieldType.BOOLEAN)
                                        .description("지금 세일 중인지 여부"))));

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleRate").value(15))
                .andExpect(jsonPath("$.basePrice").value(12_900))
                .andExpect(jsonPath("$.skus[0].price").value(12_900))
                .andExpect(jsonPath("$.skus[0].salePrice").value(10_960))
                .andExpect(jsonPath("$.skus[1].price").value(14_900))
                .andExpect(jsonPath("$.skus[1].salePrice").value(12_660));
    }

    @Test
    void 기간이_지난_세일은_정가로_나온다() throws Exception {
        Instant now = Instant.now();

        putSale(productId, saleBody(15, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale.rate").value(15))
                .andExpect(jsonPath("$.sale.active").value(false));

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleRate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.skus[0].salePrice").value(12_900))
                .andExpect(jsonPath("$.skus[1].salePrice").value(14_900));
    }

    @Test
    void 새로_건_세일은_기존_세일을_교체한다() throws Exception {
        Instant now = Instant.now();
        putSale(productId, saleBody(15, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isOk());

        putSale(productId, saleBody(30, now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale.rate").value(30));

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(jsonPath("$.saleRate").value(30))
                .andExpect(jsonPath("$.skus[0].salePrice").value(9_030));
    }

    @Test
    void 세일을_끝낸다() throws Exception {
        Instant now = Instant.now();
        putSale(productId, saleBody(15, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/products/{productId}/sale", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent())
                .andDo(document("admin-product/end-sale",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        requestHeaders(AUTHORIZATION)));

        mockMvc.perform(get("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(jsonPath("$.saleRate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.skus[0].salePrice").value(12_900));
    }

    @Test
    void 세일이_없어도_세일_종료는_204다() throws Exception {
        mockMvc.perform(delete("/api/admin/products/{productId}/sale", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 종료가_시작보다_빠르면_400_INVALID_SALE_PERIOD() throws Exception {
        Instant now = Instant.now();

        putSale(productId, saleBody(15, now.plus(1, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SALE_PERIOD"));
        putSale(productId, saleBody(15, now, now))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SALE_PERIOD"));
    }

    @Test
    void 할인율_91은_400() throws Exception {
        Instant now = Instant.now();

        putSale(productId, saleBody(91, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        putSale(productId, saleBody(0, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 기간이_없으면_400() throws Exception {
        putSale(productId, """
                {"rate":15,"startsAt":null,"endsAt":null}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_상품에_세일을_걸면_404() throws Exception {
        Instant now = Instant.now();

        putSale(999_999L, saleBody(15, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 일반_회원은_세일을_걸_수_없다() throws Exception {
        Instant now = Instant.now();

        mockMvc.perform(put("/api/admin/products/{productId}/sale", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(15, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isForbidden());
    }
}
