package com.sprint.mission.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class AdminSkuApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    BrandService brandService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long brandId;
    Long tonerId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        tonerId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private Long register(String skus) throws Exception {
        String body = """
                {"brandId":%s,"categoryId":%s,"name":"독도 토너","summary":null,
                 "description":"피부결을 정돈하는 토너다.","ingredients":null,
                 "basePrice":12900,"imageUrls":["https://img.example.com/main.jpg"],"skus":%s}"""
                .formatted(brandId, tonerId, skus);
        String location = mockMvc.perform(post("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private Long registerTwoSkus() throws Exception {
        return register("""
                [{"name":"150ml","additionalPrice":0,"stock":10},
                 {"name":"300ml","additionalPrice":8000,"stock":0}]""");
    }

    private Long skuId(Long productId, String name) {
        return jdbcTemplate.queryForObject(
                "select id from sku where product_id = ? and name = ?", Long.class, productId, name);
    }

    private int stock(Long skuId) {
        return jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId);
    }

    private ResultActions addSku(Long productId, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/products/{productId}/skus", productId)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions updateSku(Long productId, Long skuId, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/products/{productId}/skus/{skuId}", productId, skuId)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions adjust(Long productId, Long skuId, int delta) throws Exception {
        return mockMvc.perform(post("/api/admin/products/{productId}/skus/{skuId}/stock-adjustments",
                        productId, skuId)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":" + delta + "}"));
    }

    @Test
    void 옵션을_추가한다() throws Exception {
        Long productId = registerTwoSkus();

        addSku(productId, """
                {"name":"  500ml  ","additionalPrice":15000,"stock":7}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.skus.length()").value(3))
                .andExpect(jsonPath("$.skus[2].name").value("500ml"))
                .andExpect(jsonPath("$.skus[2].price").value(12_900 + 15_000))
                .andExpect(jsonPath("$.skus[2].stock").value(7))
                .andDo(document("admin-sku/create",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING)
                                        .description("옵션 이름 (1~50자, 상품 안에서 중복 불가)"),
                                fieldWithPath("additionalPrice").type(JsonFieldType.NUMBER)
                                        .description("추가금액 (원, 0~10,000,000)"),
                                fieldWithPath("stock").type(JsonFieldType.NUMBER).description("처음 재고 (0~100,000)")),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("상품 상태"),
                                fieldWithPath("brandId").type(JsonFieldType.NUMBER).description("브랜드 ID"),
                                fieldWithPath("brandName").type(JsonFieldType.STRING).description("브랜드 이름"),
                                fieldWithPath("categoryPath").type(JsonFieldType.ARRAY)
                                        .description("루트부터 상품 카테고리까지의 이름 경로"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("summary").type(JsonFieldType.STRING).optional()
                                        .description("한 줄 요약. 없으면 `null`"),
                                fieldWithPath("description").type(JsonFieldType.STRING).description("상세 설명"),
                                fieldWithPath("ingredients").type(JsonFieldType.STRING).optional()
                                        .description("전성분. 없으면 `null`"),
                                fieldWithPath("basePrice").type(JsonFieldType.NUMBER).description("정가 (원)"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY).description("이미지 URL 목록"),
                                fieldWithPath("skus").type(JsonFieldType.ARRAY)
                                        .description("옵션(SKU) 목록. 새 옵션이 마지막에 붙는다"),
                                fieldWithPath("skus[].id").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("skus[].name").type(JsonFieldType.STRING).description("옵션 이름"),
                                fieldWithPath("skus[].additionalPrice").type(JsonFieldType.NUMBER)
                                        .description("추가금액 (원)"),
                                fieldWithPath("skus[].price").type(JsonFieldType.NUMBER)
                                        .description("옵션 가격 (원). 정가에 추가금액을 더한 값이다"),
                                fieldWithPath("skus[].stock").type(JsonFieldType.NUMBER).description("재고 수량"),
                                fieldWithPath("sale").type(JsonFieldType.OBJECT).optional()
                                        .description("세일 정보. 걸린 세일이 없으면 `null`이고, 기간이 아닌 세일도 보인다"),
                                fieldWithPath("sale.rate").type(JsonFieldType.NUMBER).optional()
                                        .description("할인율 (%)"),
                                fieldWithPath("sale.startsAt").type(JsonFieldType.STRING).optional()
                                        .description("세일 시작 시각"),
                                fieldWithPath("sale.endsAt").type(JsonFieldType.STRING).optional()
                                        .description("세일 종료 시각"),
                                fieldWithPath("sale.active").type(JsonFieldType.BOOLEAN).optional()
                                        .description("지금 세일 중인지 여부"))));
    }

    @Test
    void 옵션_이름이_겹치면_추가할_수_없다() throws Exception {
        Long productId = registerTwoSkus();

        addSku(productId, "{\"name\":\"150ml\",\"additionalPrice\":0,\"stock\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU_NAME"));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sku where product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void 입력_규칙에_맞지_않는_옵션은_400() throws Exception {
        Long productId = registerTwoSkus();

        addSku(productId, "{\"name\":\"  \",\"additionalPrice\":0,\"stock\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));
        addSku(productId, "{\"name\":\"500ml\",\"additionalPrice\":0,\"stock\":100001}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("stock")));
    }

    @Test
    void 삭제된_상품에는_옵션을_추가할_수_없다() throws Exception {
        Long productId = registerTwoSkus();
        jdbcTemplate.update("update product set status = 'DELETED'");

        addSku(productId, "{\"name\":\"500ml\",\"additionalPrice\":0,\"stock\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 옵션을_수정한다() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "300ml");

        updateSku(productId, skuId, "{\"name\":\"  350ml  \",\"additionalPrice\":9000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.skus[1].id").value(skuId))
                .andExpect(jsonPath("$.skus[1].name").value("350ml"))
                .andExpect(jsonPath("$.skus[1].additionalPrice").value(9_000))
                .andExpect(jsonPath("$.skus[1].price").value(12_900 + 9_000))
                .andExpect(jsonPath("$.skus[1].stock").value(0))
                .andDo(document("admin-sku/update",
                        pathParameters(
                                parameterWithName("productId").description("상품 ID"),
                                parameterWithName("skuId").description("SKU ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING)
                                        .description("옵션 이름 (1~50자, 상품 안에서 중복 불가)"),
                                fieldWithPath("additionalPrice").type(JsonFieldType.NUMBER)
                                        .description("추가금액 (원, 0~10,000,000)")),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("상품 상태"),
                                fieldWithPath("brandId").type(JsonFieldType.NUMBER).description("브랜드 ID"),
                                fieldWithPath("brandName").type(JsonFieldType.STRING).description("브랜드 이름"),
                                fieldWithPath("categoryPath").type(JsonFieldType.ARRAY)
                                        .description("루트부터 상품 카테고리까지의 이름 경로"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("summary").type(JsonFieldType.STRING).optional()
                                        .description("한 줄 요약. 없으면 `null`"),
                                fieldWithPath("description").type(JsonFieldType.STRING).description("상세 설명"),
                                fieldWithPath("ingredients").type(JsonFieldType.STRING).optional()
                                        .description("전성분. 없으면 `null`"),
                                fieldWithPath("basePrice").type(JsonFieldType.NUMBER).description("정가 (원)"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY).description("이미지 URL 목록"),
                                fieldWithPath("skus").type(JsonFieldType.ARRAY).description("옵션(SKU) 목록"),
                                fieldWithPath("skus[].id").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("skus[].name").type(JsonFieldType.STRING).description("옵션 이름"),
                                fieldWithPath("skus[].additionalPrice").type(JsonFieldType.NUMBER)
                                        .description("추가금액 (원)"),
                                fieldWithPath("skus[].price").type(JsonFieldType.NUMBER)
                                        .description("옵션 가격 (원). 정가에 추가금액을 더한 값이다"),
                                fieldWithPath("skus[].stock").type(JsonFieldType.NUMBER)
                                        .description("재고 수량. 옵션 수정으로는 바뀌지 않는다"),
                                fieldWithPath("sale").type(JsonFieldType.OBJECT).optional()
                                        .description("세일 정보. 걸린 세일이 없으면 `null`이고, 기간이 아닌 세일도 보인다"),
                                fieldWithPath("sale.rate").type(JsonFieldType.NUMBER).optional()
                                        .description("할인율 (%)"),
                                fieldWithPath("sale.startsAt").type(JsonFieldType.STRING).optional()
                                        .description("세일 시작 시각"),
                                fieldWithPath("sale.endsAt").type(JsonFieldType.STRING).optional()
                                        .description("세일 종료 시각"),
                                fieldWithPath("sale.active").type(JsonFieldType.BOOLEAN).optional()
                                        .description("지금 세일 중인지 여부"))));
    }

    @Test
    void 옵션_수정은_자기_이름은_허용하고_다른_옵션_이름은_거절한다() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "300ml");

        updateSku(productId, skuId, "{\"name\":\"300ml\",\"additionalPrice\":5000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skus[1].additionalPrice").value(5_000));
        updateSku(productId, skuId, "{\"name\":\"150ml\",\"additionalPrice\":5000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU_NAME"));
    }

    @Test
    void 없는_옵션_수정은_404() throws Exception {
        Long productId = registerTwoSkus();

        updateSku(productId, 9999L, "{\"name\":\"500ml\",\"additionalPrice\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
    }

    @Test
    void 입고하면_재고가_늘어난다() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "150ml");

        adjust(productId, skuId, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value(skuId))
                .andExpect(jsonPath("$.stock").value(15))
                .andDo(document("admin-sku/adjust-stock",
                        pathParameters(
                                parameterWithName("productId").description("상품 ID"),
                                parameterWithName("skuId").description("SKU ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(fieldWithPath("delta").type(JsonFieldType.NUMBER)
                                .description("재고 증감량. 양수는 입고, 음수는 출고다 (0 제외, 절댓값 100,000 이하)")),
                        responseFields(
                                fieldWithPath("skuId").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("stock").type(JsonFieldType.NUMBER).description("조정된 뒤의 재고 수량"))));

        assertThat(stock(skuId)).isEqualTo(15);
    }

    @Test
    void 재고보다_많이_빼면_409이고_재고는_그대로다() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "150ml");

        adjust(productId, skuId, -11)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_ENOUGH"));

        assertThat(stock(skuId)).isEqualTo(10);

        adjust(productId, skuId, -10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0));
    }

    @Test
    void 재고는_100000을_넘을_수_없다() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "150ml");

        adjust(productId, skuId, 100_000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(stock(skuId)).isEqualTo(10);

        adjust(productId, skuId, 99_990)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(100_000));
    }

    @Test
    void delta가_0이면_400() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "150ml");

        adjust(productId, skuId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("delta")));
        adjust(productId, skuId, 100_001)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("delta")));
        adjust(productId, skuId, -100_001)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("delta")));

        assertThat(stock(skuId)).isEqualTo(10);
    }

    @Test
    void 다른_상품의_SKU_id면_404() throws Exception {
        Long productId = registerTwoSkus();
        Long otherProductId = register("[{\"name\":\"50ml\",\"additionalPrice\":0,\"stock\":3}]");
        Long otherSkuId = skuId(otherProductId, "50ml");

        adjust(productId, otherSkuId, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
        updateSku(productId, otherSkuId, "{\"name\":\"500ml\",\"additionalPrice\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));

        assertThat(stock(otherSkuId)).isEqualTo(3);
    }

    @Test
    void 삭제된_상품의_재고는_조정할_수_없다() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "150ml");
        jdbcTemplate.update("update product set status = 'DELETED'");

        adjust(productId, skuId, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 판매중지한_상품의_재고는_조정할_수_있다() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "150ml");
        jdbcTemplate.update("update product set status = 'STOPPED'");

        adjust(productId, skuId, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(12));
    }

    @Test
    void 이십일번째_옵션은_400() throws Exception {
        String twenty = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "{\"name\":\"옵션" + i + "\",\"additionalPrice\":0,\"stock\":1}")
                .collect(Collectors.joining(",", "[", "]"));
        Long productId = register(twenty);

        addSku(productId, "{\"name\":\"옵션21\",\"additionalPrice\":0,\"stock\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SKU_LIMIT_EXCEEDED"));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sku where product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(20);
    }

    @Test
    void USER_토큰이면_403() throws Exception {
        Long productId = registerTwoSkus();
        Long skuId = skuId(productId, "150ml");

        mockMvc.perform(post("/api/admin/products/{productId}/skus/{skuId}/stock-adjustments", productId, skuId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
