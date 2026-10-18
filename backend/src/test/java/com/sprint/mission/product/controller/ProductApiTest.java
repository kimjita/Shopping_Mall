package com.sprint.mission.product.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.product.repository.SkuRepository;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;

class ProductApiTest extends ApiTest {

    @Autowired
    BrandService brandService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    SkuRepository skuRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long brandId;
    Long tonerId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        tonerId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private Long product(int firstStock, int secondStock) {
        Product product = productRepository.save(Product.create(brandId, tonerId, "독도 토너", "순한 각질 케어 토너",
                "피부결을 정돈하는 토너다.", "정제수, 부틸렌글라이콜", 12_900,
                List.of("https://img.example.com/main.jpg", "https://img.example.com/sub.jpg")));
        skuRepository.save(Sku.create(product.getId(), "150ml", 0, firstStock));
        skuRepository.save(Sku.create(product.getId(), "300ml", 8_000, secondStock));
        return product.getId();
    }

    @Test
    void 비회원이_상품_상세를_본다() throws Exception {
        Long productId = product(10, 0);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.name").value("독도 토너"))
                .andExpect(jsonPath("$.brand.id").value(brandId))
                .andExpect(jsonPath("$.brand.name").value("라운드랩"))
                .andExpect(jsonPath("$.categoryPath[*].name").value(contains("스킨케어", "토너")))
                .andExpect(jsonPath("$.categoryPath[1].id").value(tonerId))
                .andExpect(jsonPath("$.basePrice").value(12_900))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("https://img.example.com/sub.jpg"))
                .andExpect(jsonPath("$.soldOut").value(false))
                .andExpect(jsonPath("$.skus[0].name").value("150ml"))
                .andExpect(jsonPath("$.skus[0].price").value(12_900))
                .andExpect(jsonPath("$.skus[0].soldOut").value(false))
                .andExpect(jsonPath("$.skus[0].stock").doesNotExist())
                .andExpect(jsonPath("$.skus[1].name").value("300ml"))
                .andExpect(jsonPath("$.skus[1].price").value(12_900 + 8_000))
                .andExpect(jsonPath("$.skus[1].soldOut").value(true))
                .andExpect(jsonPath("$.stock").doesNotExist())
                .andDo(document("product/get",
                        pathParameters(parameterWithName("productId").description("상품 ID")),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("brand").type(JsonFieldType.OBJECT).description("브랜드"),
                                fieldWithPath("brand.id").type(JsonFieldType.NUMBER).description("브랜드 ID"),
                                fieldWithPath("brand.name").type(JsonFieldType.STRING).description("브랜드 이름"),
                                fieldWithPath("categoryPath").type(JsonFieldType.ARRAY)
                                        .description("루트부터 상품 카테고리까지의 경로"),
                                fieldWithPath("categoryPath[].id").type(JsonFieldType.NUMBER)
                                        .description("카테고리 ID"),
                                fieldWithPath("categoryPath[].name").type(JsonFieldType.STRING)
                                        .description("카테고리 이름"),
                                fieldWithPath("summary").type(JsonFieldType.STRING).optional()
                                        .description("한 줄 요약. 없으면 `null`"),
                                fieldWithPath("description").type(JsonFieldType.STRING).description("상세 설명"),
                                fieldWithPath("ingredients").type(JsonFieldType.STRING).optional()
                                        .description("전성분. 없으면 `null`"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY)
                                        .description("이미지 URL 목록. 첫 번째가 대표 이미지다"),
                                fieldWithPath("basePrice").type(JsonFieldType.NUMBER).description("정가 (원)"),
                                fieldWithPath("soldOut").type(JsonFieldType.BOOLEAN)
                                        .description("품절 여부. 모든 옵션이 품절이면 `true`다"),
                                fieldWithPath("skus").type(JsonFieldType.ARRAY).description("옵션(SKU) 목록"),
                                fieldWithPath("skus[].id").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("skus[].name").type(JsonFieldType.STRING).description("옵션 이름"),
                                fieldWithPath("skus[].price").type(JsonFieldType.NUMBER)
                                        .description("옵션 가격 (원). 정가에 추가금액을 더한 값이다"),
                                fieldWithPath("skus[].soldOut").type(JsonFieldType.BOOLEAN)
                                        .description("옵션 품절 여부. 재고 수량은 공개하지 않는다"))));
    }

    @Test
    void 모든_SKU가_품절이면_상품도_품절이다() throws Exception {
        Long productId = product(0, 0);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldOut").value(true))
                .andExpect(jsonPath("$.skus[0].soldOut").value(true))
                .andExpect(jsonPath("$.skus[1].soldOut").value(true));
    }

    @Test
    void 판매중지_상품은_404() throws Exception {
        Long productId = product(10, 10);
        jdbcTemplate.update("update product set status = 'STOPPED'");

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 삭제된_상품은_404() throws Exception {
        Long productId = product(10, 10);
        jdbcTemplate.update("update product set status = 'DELETED'");

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 없는_상품은_404() throws Exception {
        mockMvc.perform(get("/api/products/{productId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
