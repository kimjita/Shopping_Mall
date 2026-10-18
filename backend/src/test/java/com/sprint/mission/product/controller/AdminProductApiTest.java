package com.sprint.mission.product.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
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
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class AdminProductApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    BrandService brandService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long brandId;
    Long tonerId;
    Long skincareId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        tonerId = categoryId("토너");
        skincareId = categoryId("스킨케어");
    }

    private Long categoryId(String name) {
        return jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
    }

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private String body(Long brand, Long category, String imageUrls, String skus) {
        return """
                {"brandId":%s,"categoryId":%s,"name":"독도 토너","summary":"순한 각질 케어 토너",
                 "description":"피부결을 정돈하는 토너다.","ingredients":"정제수, 부틸렌글라이콜",
                 "basePrice":12900,"imageUrls":%s,"skus":%s}"""
                .formatted(brand, category, imageUrls, skus);
    }

    private static final String TWO_SKUS = """
            [{"name":"150ml","additionalPrice":0,"stock":10},
             {"name":"300ml","additionalPrice":8000,"stock":0}]""";
    private static final String TWO_IMAGES = """
            ["https://img.example.com/main.jpg","https://img.example.com/sub.jpg"]""";

    private static Long productId(String location) {
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private ResultActions register(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 관리자가_상품을_등록하고_상세를_본다() throws Exception {
        String location = register(admin(), body(brandId, tonerId, TWO_IMAGES, TWO_SKUS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/admin/products/")))
                .andDo(document("admin-product/create",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("brandId").type(JsonFieldType.NUMBER).description("브랜드 ID"),
                                fieldWithPath("categoryId").type(JsonFieldType.NUMBER)
                                        .description("카테고리 ID. 가장 아래 단계 카테고리여야 한다"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("상품명 (1~100자)"),
                                fieldWithPath("summary").type(JsonFieldType.STRING).optional()
                                        .description("한 줄 요약 (200자 이하, 생략 가능)"),
                                fieldWithPath("description").type(JsonFieldType.STRING).description("상세 설명"),
                                fieldWithPath("ingredients").type(JsonFieldType.STRING).optional()
                                        .description("전성분 (생략 가능)"),
                                fieldWithPath("basePrice").type(JsonFieldType.NUMBER)
                                        .description("정가 (원, 100~10,000,000)"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY)
                                        .description("이미지 URL 1~10개. 첫 번째가 대표 이미지다. 각 500자 이하, `http(s)://`로 시작한다"),
                                fieldWithPath("skus").type(JsonFieldType.ARRAY).description("옵션(SKU) 1~20개"),
                                fieldWithPath("skus[].name").type(JsonFieldType.STRING)
                                        .description("옵션 이름 (1~50자, 상품 안에서 중복 불가)"),
                                fieldWithPath("skus[].additionalPrice").type(JsonFieldType.NUMBER)
                                        .description("추가금액 (원, 0~10,000,000)"),
                                fieldWithPath("skus[].stock").type(JsonFieldType.NUMBER)
                                        .description("재고 (0~100,000)")),
                        responseHeaders(headerWithName(HttpHeaders.LOCATION)
                                .description("등록된 상품의 관리자 상세 URL")),
                        responseFields(fieldWithPath("id").type(JsonFieldType.NUMBER).description("상품 ID"))))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(get("/api/admin/products/{productId}", productId(location))
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"))
                .andExpect(jsonPath("$.brandId").value(brandId))
                .andExpect(jsonPath("$.brandName").value("라운드랩"))
                .andExpect(jsonPath("$.categoryPath[0]").value("스킨케어"))
                .andExpect(jsonPath("$.categoryPath[1]").value("토너"))
                .andExpect(jsonPath("$.categoryPath.length()").value(2))
                .andExpect(jsonPath("$.name").value("독도 토너"))
                .andExpect(jsonPath("$.basePrice").value(12_900))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("https://img.example.com/sub.jpg"))
                .andExpect(jsonPath("$.skus[0].name").value("150ml"))
                .andExpect(jsonPath("$.skus[0].price").value(12_900))
                .andExpect(jsonPath("$.skus[0].stock").value(10))
                .andExpect(jsonPath("$.skus[1].name").value("300ml"))
                .andExpect(jsonPath("$.skus[1].additionalPrice").value(8_000))
                .andExpect(jsonPath("$.skus[1].price").value(12_900 + 8_000))
                .andExpect(jsonPath("$.skus[1].stock").value(0))
                .andDo(document("admin-product/get",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        requestHeaders(AUTHORIZATION),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("status").type(JsonFieldType.STRING)
                                        .description("상품 상태 (`ON_SALE`, `STOPPED`)"),
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
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY)
                                        .description("이미지 URL 목록. 첫 번째가 대표 이미지다"),
                                fieldWithPath("skus").type(JsonFieldType.ARRAY).description("옵션(SKU) 목록. 등록 순서다"),
                                fieldWithPath("skus[].id").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("skus[].name").type(JsonFieldType.STRING).description("옵션 이름"),
                                fieldWithPath("skus[].additionalPrice").type(JsonFieldType.NUMBER)
                                        .description("추가금액 (원)"),
                                fieldWithPath("skus[].price").type(JsonFieldType.NUMBER)
                                        .description("옵션 가격 (원). 정가에 추가금액을 더한 값이다"),
                                fieldWithPath("skus[].stock").type(JsonFieldType.NUMBER).description("재고 수량"))));
    }

    @Test
    void 말단이_아닌_카테고리는_400_CATEGORY_NOT_LEAF() throws Exception {
        register(admin(), body(brandId, skincareId, TWO_IMAGES, TWO_SKUS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_LEAF"));
    }

    @Test
    void 없는_브랜드는_404_BRAND_NOT_FOUND() throws Exception {
        register(admin(), body(9999L, tonerId, TWO_IMAGES, TWO_SKUS))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));
    }

    @Test
    void 요청_안_SKU_이름이_겹치면_409() throws Exception {
        String dup = """
                [{"name":"150ml","additionalPrice":0,"stock":10},
                 {"name":"150ml","additionalPrice":1000,"stock":3}]""";
        register(admin(), body(brandId, tonerId, TWO_IMAGES, dup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU_NAME"));

        Integer count = jdbcTemplate.queryForObject("select count(*) from product", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }

    @Test
    void 이미지가_없거나_http가_아니면_400() throws Exception {
        register(admin(), body(brandId, tonerId, "[]", TWO_SKUS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("imageUrls")));
        register(admin(), body(brandId, tonerId, "[\"ftp://x\"]", TWO_SKUS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(startsWith("imageUrls"))));
    }

    @Test
    void USER_토큰이면_403() throws Exception {
        register(bearer(1L, Role.USER), body(brandId, tonerId, TWO_IMAGES, TWO_SKUS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 비회원은_상품을_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(brandId, tonerId, TWO_IMAGES, TWO_SKUS)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_상품_상세는_404() throws Exception {
        mockMvc.perform(get("/api/admin/products/{productId}", 9999L).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 삭제된_상품_상세는_404() throws Exception {
        String location = register(admin(), body(brandId, tonerId, TWO_IMAGES, TWO_SKUS))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        jdbcTemplate.update("update product set status = 'DELETED'");

        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    private static final String UPDATE_BODY = """
            {"brandId":%s,"categoryId":%s,"name":"  수정된 토너  ","summary":null,
             "description":"수정된 설명이다.","ingredients":"정제수",
             "basePrice":15000,"imageUrls":["https://img.example.com/new.jpg"]}""";

    private Long registered() throws Exception {
        return productId(register(admin(), body(brandId, tonerId, TWO_IMAGES, TWO_SKUS))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION));
    }

    private ResultActions changeStatus(Long productId, String status) throws Exception {
        return mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    @Test
    void 관리자가_상품을_수정한다() throws Exception {
        Long productId = registered();
        Long serumId = categoryId("에센스·세럼");
        Long otherBrandId = brandService.create(new BrandRequest("닥터지", null)).id();

        mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY.formatted(otherBrandId, serumId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.brandName").value("닥터지"))
                .andExpect(jsonPath("$.categoryPath[1]").value("에센스·세럼"))
                .andExpect(jsonPath("$.name").value("수정된 토너"))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.description").value("수정된 설명이다."))
                .andExpect(jsonPath("$.basePrice").value(15_000))
                .andExpect(jsonPath("$.imageUrls.length()").value(1))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://img.example.com/new.jpg"))
                .andExpect(jsonPath("$.skus.length()").value(2))
                .andExpect(jsonPath("$.skus[1].price").value(15_000 + 8_000))
                .andDo(document("admin-product/update",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("brandId").type(JsonFieldType.NUMBER).description("브랜드 ID"),
                                fieldWithPath("categoryId").type(JsonFieldType.NUMBER)
                                        .description("카테고리 ID. 가장 아래 단계 카테고리여야 한다"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("상품명 (1~100자)"),
                                fieldWithPath("summary").type(JsonFieldType.STRING).optional()
                                        .description("한 줄 요약 (200자 이하, 생략하면 비워진다)"),
                                fieldWithPath("description").type(JsonFieldType.STRING).description("상세 설명"),
                                fieldWithPath("ingredients").type(JsonFieldType.STRING).optional()
                                        .description("전성분 (생략하면 비워진다)"),
                                fieldWithPath("basePrice").type(JsonFieldType.NUMBER)
                                        .description("정가 (원, 100~10,000,000)"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY)
                                        .description("이미지 URL 1~10개. 보낸 목록으로 통째로 바뀐다")),
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
                                        .description("옵션 가격 (원). 새 정가에 추가금액을 더한 값이다"),
                                fieldWithPath("skus[].stock").type(JsonFieldType.NUMBER).description("재고 수량"))));

        mockMvc.perform(get("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.name").value("수정된 토너"))
                .andExpect(jsonPath("$.imageUrls.length()").value(1));
    }

    @Test
    void 수정할_때도_말단이_아닌_카테고리와_없는_브랜드는_거절한다() throws Exception {
        Long productId = registered();

        mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY.formatted(brandId, skincareId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_LEAF"));
        mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY.formatted(9999L, tonerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));
        mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY.formatted(brandId, tonerId).replace("\"basePrice\":15000", "\"basePrice\":50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 판매중지한_상품도_관리자는_본다() throws Exception {
        Long productId = registered();

        changeStatus(productId, "STOPPED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));

        mockMvc.perform(get("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound());

        changeStatus(productId, "ON_SALE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"));
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk());
    }

    @Test
    void 삭제한_상품은_관리자도_404이고_상태를_되돌릴_수_없다() throws Exception {
        Long productId = registered();

        mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andDo(document("admin-product/change-status",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(fieldWithPath("status").type(JsonFieldType.STRING)
                                .description("바꿀 상태 (`ON_SALE`, `STOPPED`, `DELETED`)")),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("바뀐 상품 상태"),
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
                                        .description("옵션 가격 (원)"),
                                fieldWithPath("skus[].stock").type(JsonFieldType.NUMBER).description("재고 수량"))));

        mockMvc.perform(get("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        changeStatus(productId, "ON_SALE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY.formatted(brandId, tonerId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상태가_없거나_알_수_없으면_400() throws Exception {
        Long productId = registered();

        mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비회원은_관리자_상품_상세를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/products/{productId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
