package com.sprint.mission.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.StockAdjustRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.AdminSkuService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class CartApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    CartService cartService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    AdminSkuService adminSkuService;

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

    private Long register(String name, long basePrice, SkuRequest... skus) {
        return adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, basePrice, List.of("https://img.example.com/main.jpg"), List.of(skus)));
    }

    /** 옵션 하나짜리 상품을 만들고 그 SKU id를 돌려준다. */
    private Long skuWithStock(int stock) {
        Long productId = register("1025 독도 토너", 14_900, new SkuRequest("기본", 0, stock));
        return skuIdsOf(productId).get(0);
    }

    private List<Long> skuIdsOf(Long productId) {
        return jdbcTemplate.queryForList("select id from sku where product_id = ? order by id", Long.class, productId);
    }

    private String body(Long skuId, int quantity) {
        return "{\"skuId\":" + skuId + ",\"quantity\":" + quantity + "}";
    }

    private ResultActions add(Long skuId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/members/me/cart/items")
                .header(HttpHeaders.AUTHORIZATION, token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(skuId, quantity)));
    }

    private ResultActions cart() throws Exception {
        return mockMvc.perform(get("/api/members/me/cart").header(HttpHeaders.AUTHORIZATION, token()));
    }

    private ResultActions changeQuantity(String token, Long cartItemId, int quantity) throws Exception {
        return mockMvc.perform(patch("/api/members/me/cart/items/{cartItemId}", cartItemId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + "}"));
    }

    private ResultActions remove(String token, Long cartItemId) throws Exception {
        return mockMvc.perform(delete("/api/members/me/cart/items/{cartItemId}", cartItemId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Long cartItemId(Long skuId) {
        return jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, memberId, skuId);
    }

    private List<Integer> quantities() {
        return jdbcTemplate.queryForList("select quantity from cart_item where member_id = ? order by id",
                Integer.class, memberId);
    }

    private void reduceStock(Long skuId, int amount) {
        Long productId = jdbcTemplate.queryForObject("select product_id from sku where id = ?", Long.class, skuId);
        adminSkuService.adjustStock(productId, skuId, new StockAdjustRequest(-amount));
    }

    private List<FieldDescriptor> itemFields(String prefix) {
        return List.of(
                fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("장바구니 항목 ID"),
                fieldWithPath(prefix + "skuId").type(JsonFieldType.NUMBER).description("옵션(SKU) ID"),
                fieldWithPath(prefix + "skuName").type(JsonFieldType.STRING).description("옵션 이름"),
                fieldWithPath(prefix + "productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                fieldWithPath(prefix + "productName").type(JsonFieldType.STRING).description("상품명"),
                fieldWithPath(prefix + "brandName").type(JsonFieldType.STRING).description("브랜드 이름"),
                fieldWithPath(prefix + "thumbnailUrl").type(JsonFieldType.STRING).optional()
                        .description("대표 이미지 URL. 상품에 이미지가 없으면 null이다"),
                fieldWithPath(prefix + "quantity").type(JsonFieldType.NUMBER).description("담은 수량 (1~99)"),
                fieldWithPath(prefix + "unitPrice").type(JsonFieldType.NUMBER)
                        .description("옵션 정가 (원). 상품 정가에 옵션 추가금액을 더한 값이다"),
                fieldWithPath(prefix + "unitSalePrice").type(JsonFieldType.NUMBER)
                        .description("옵션 판매가 (원). 진행 중인 세일을 적용한 값이고 세일이 없으면 `unitPrice`와 같다"),
                fieldWithPath(prefix + "lineTotal").type(JsonFieldType.NUMBER)
                        .description("줄 합계 (원). `unitSalePrice` × `quantity`다"),
                fieldWithPath(prefix + "status").type(JsonFieldType.STRING)
                        .description("구매 가능 상태. `AVAILABLE`, `NOT_ENOUGH_STOCK`, `SOLD_OUT`, `UNAVAILABLE` 중 하나다"));
    }

    @Test
    void 장바구니에_담고_조회한다() throws Exception {
        Long productId = register("1025 독도 토너", 14_900, new SkuRequest("기본", 0, 10));
        Long skuId = skuIdsOf(productId).get(0);

        mockMvc.perform(post("/api/members/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(skuId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skuId").value(skuId))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.lineTotal").value(29_800))
                .andDo(document("cart/add",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("skuId").type(JsonFieldType.NUMBER).description("담을 옵션(SKU) ID"),
                                fieldWithPath("quantity").type(JsonFieldType.NUMBER)
                                        .description("담을 수량 (1~99). 이미 담긴 옵션이면 기존 수량에 더해진다")),
                        responseFields(itemFields(""))));

        cart().andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].skuId").value(skuId))
                .andExpect(jsonPath("$.items[0].skuName").value("기본"))
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].productName").value("1025 독도 토너"))
                .andExpect(jsonPath("$.items[0].brandName").value("라운드랩"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(14_900))
                .andExpect(jsonPath("$.items[0].unitSalePrice").value(14_900))
                .andExpect(jsonPath("$.items[0].lineTotal").value(29_800))
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andDo(document("cart/get",
                        requestHeaders(AUTHORIZATION),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("담은 순서의 장바구니 항목"))
                                .andWithPrefix("items[].", itemFields("").toArray(FieldDescriptor[]::new))));
    }

    @Test
    void 같은_SKU를_다시_담으면_수량이_합쳐진다() throws Exception {
        Long skuId = skuWithStock(10);

        add(skuId, 2).andExpect(status().isCreated());
        add(skuId, 3).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.lineTotal").value(74_500));

        assertThat(quantities()).containsExactly(5);
        cart().andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    void 합친_수량이_재고보다_많으면_409이고_기존_수량은_그대로다() throws Exception {
        Long skuId = skuWithStock(5);
        add(skuId, 3).andExpect(status().isCreated());

        add(skuId, 3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_ENOUGH"));

        assertThat(quantities()).containsExactly(3);
        add(skuId, 6).andExpect(status().isConflict());
        Long other = skuWithStock(2);
        add(other, 3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_ENOUGH"));
        assertThat(quantities()).containsExactly(3);
    }

    @Test
    void 수량_99를_넘으면_400() throws Exception {
        Long skuId = skuWithStock(500);
        add(skuId, 99).andExpect(status().isCreated());

        // 합쳐서 100이 되는 경우
        add(skuId, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(quantities()).containsExactly(99);

        // 요청 값 자체가 범위를 벗어나는 경우
        Long other = skuWithStock(500);
        add(other, 100).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        add(other, 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        add(other, -1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/members/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(quantities()).containsExactly(99);

        // 수량 변경도 같은 범위를 지킨다
        Long itemId = cartItemId(skuId);
        changeQuantity(token(), itemId, 100).andExpect(status().isBadRequest());
        changeQuantity(token(), itemId, 0).andExpect(status().isBadRequest());
        assertThat(quantities()).containsExactly(99);
    }

    @Test
    void 수량에_소수를_보내면_400() throws Exception {
        Long skuId = skuWithStock(10);
        mockMvc.perform(post("/api/members/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + skuId + ",\"quantity\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(quantities()).isEmpty();
    }

    @Test
    void 수량을_바꾸고_뺀다() throws Exception {
        Long skuId = skuWithStock(10);
        add(skuId, 2);
        Long itemId = cartItemId(skuId);

        mockMvc.perform(patch("/api/members/me/cart/items/{cartItemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.lineTotal").value(59_600))
                .andDo(document("cart/change-quantity",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("cartItemId").description("수량을 바꿀 장바구니 항목 ID")),
                        requestFields(fieldWithPath("quantity").type(JsonFieldType.NUMBER)
                                .description("바꿀 수량 (1~99). 기존 수량을 대체한다")),
                        responseFields(itemFields(""))));
        assertThat(quantities()).containsExactly(4);

        // 재고보다 많이는 바꿀 수 없고 수량은 그대로다
        changeQuantity(token(), itemId, 11).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_ENOUGH"));
        assertThat(quantities()).containsExactly(4);

        mockMvc.perform(delete("/api/members/me/cart/items/{cartItemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andDo(document("cart/remove",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("cartItemId").description("뺄 장바구니 항목 ID"))));
        assertThat(quantities()).isEmpty();
        cart().andExpect(jsonPath("$.items").value(empty()));
        remove(token(), itemId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
        changeQuantity(token(), itemId, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void 오십_한_번째_줄은_400_CART_LIMIT_EXCEEDED() throws Exception {
        // 옵션은 상품당 20개까지라 상품 세 개에 나눠 50개를 만든다
        List<Long> skuIds = new ArrayList<>();
        for (int product = 0; product < 3; product++) {
            List<SkuRequest> skus = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                skus.add(new SkuRequest("옵션" + i, 0, 10));
            }
            Long productId = register("토너 " + product, 10_000, skus.toArray(SkuRequest[]::new));
            skuIds.addAll(skuIdsOf(productId));
        }
        for (int i = 0; i < 50; i++) {
            cartService.add(memberId, new CartAddRequest(skuIds.get(i), 1));
        }

        add(skuIds.get(50), 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_LIMIT_EXCEEDED"));
        assertThat(quantities()).hasSize(50);

        // 이미 담은 옵션을 다시 담는 것은 줄이 늘지 않으므로 50줄에서도 된다
        add(skuIds.get(0), 2).andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(3));
        assertThat(quantities()).hasSize(50);
    }

    @Test
    void 담은_뒤_재고가_줄면_NOT_ENOUGH_STOCK_또는_SOLD_OUT() throws Exception {
        Long skuId = skuWithStock(5);
        add(skuId, 3);

        reduceStock(skuId, 3);
        cart().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].status").value("NOT_ENOUGH_STOCK"));

        reduceStock(skuId, 2);
        cart().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SOLD_OUT"));
    }

    @Test
    void 담은_뒤_판매중지되거나_삭제되면_UNAVAILABLE() throws Exception {
        Long productId = register("1025 독도 토너", 14_900, new SkuRequest("기본", 0, 10));
        Long skuId = skuIdsOf(productId).get(0);
        add(skuId, 2);

        adminProductService.changeStatus(productId, new ProductStatusRequest(ProductStatus.STOPPED));
        cart().andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("1025 독도 토너"))
                .andExpect(jsonPath("$.items[0].status").value("UNAVAILABLE"));

        adminProductService.changeStatus(productId, new ProductStatusRequest(ProductStatus.DELETED));
        cart().andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("1025 독도 토너"))
                .andExpect(jsonPath("$.items[0].brandName").value("라운드랩"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(14_900))
                .andExpect(jsonPath("$.items[0].status").value("UNAVAILABLE"));
    }

    @Test
    void 판매중지된_뒤에는_수량을_바꿀_수_없지만_뺄_수는_있다() throws Exception {
        Long productId = register("1025 독도 토너", 14_900, new SkuRequest("기본", 0, 10));
        Long skuId = skuIdsOf(productId).get(0);
        add(skuId, 2);
        Long itemId = cartItemId(skuId);
        adminProductService.changeStatus(productId, new ProductStatusRequest(ProductStatus.STOPPED));

        changeQuantity(token(), itemId, 3).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertThat(quantities()).containsExactly(2);

        remove(token(), itemId).andExpect(status().isNoContent());
        assertThat(quantities()).isEmpty();
    }

    @Test
    void 세일가는_SKU마다_계산한다() throws Exception {
        Long productId = register("1025 독도 토너", 14_900,
                new SkuRequest("기본", 0, 10), new SkuRequest("대용량", 2_000, 10));
        Instant now = Instant.now();
        adminProductService.startSale(productId, new SaleRequest(15, now.minusSeconds(3_600), now.plusSeconds(3_600)));
        List<Long> skuIds = skuIdsOf(productId);
        add(skuIds.get(0), 2);
        add(skuIds.get(1), 3);

        cart().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].skuName").value("기본"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(14_900))
                .andExpect(jsonPath("$.items[0].unitSalePrice").value(12_660))
                .andExpect(jsonPath("$.items[0].lineTotal").value(25_320))
                .andExpect(jsonPath("$.items[1].skuName").value("대용량"))
                .andExpect(jsonPath("$.items[1].unitPrice").value(16_900))
                .andExpect(jsonPath("$.items[1].unitSalePrice").value(14_360))
                .andExpect(jsonPath("$.items[1].lineTotal").value(43_080));

        // 공개 상세가 보여 주는 옵션별 판매가와 같다
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skus[0].price").value(14_900))
                .andExpect(jsonPath("$.skus[0].salePrice").value(12_660))
                .andExpect(jsonPath("$.skus[1].price").value(16_900))
                .andExpect(jsonPath("$.skus[1].salePrice").value(14_360));
    }

    @Test
    void 다른_회원의_항목은_수정도_삭제도_404() throws Exception {
        Long skuId = skuWithStock(10);
        add(skuId, 2);
        Long itemId = cartItemId(skuId);
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01099998888");
        String otherToken = bearer(other, Role.USER);

        changeQuantity(otherToken, itemId, 5).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
        remove(otherToken, itemId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));

        assertThat(quantities()).containsExactly(2);
        mockMvc.perform(get("/api/members/me/cart").header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(jsonPath("$.items").value(empty()));
    }

    @Test
    void 판매중지_상품은_담을_수_없다() throws Exception {
        Long productId = register("1025 독도 토너", 14_900, new SkuRequest("기본", 0, 10));
        Long skuId = skuIdsOf(productId).get(0);
        adminProductService.changeStatus(productId, new ProductStatusRequest(ProductStatus.STOPPED));

        add(skuId, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        adminProductService.changeStatus(productId, new ProductStatusRequest(ProductStatus.DELETED));
        add(skuId, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertThat(quantities()).isEmpty();
    }

    @Test
    void 없는_SKU는_404() throws Exception {
        add(999_999L, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
    }

    @Test
    void 조회의_쿼리_수는_항목_수와_무관하다() throws Exception {
        for (int i = 0; i < 2; i++) {
            cartService.add(memberId, new CartAddRequest(skuWithStock(10), 1));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        cart().andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        long withTwo = statistics.getPrepareStatementCount();

        for (int i = 0; i < 8; i++) {
            cartService.add(memberId, new CartAddRequest(skuWithStock(10), 1));
        }
        statistics.clear();
        cart().andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10));
        long withTen = statistics.getPrepareStatementCount();

        assertThat(withTen).isEqualTo(withTwo);
    }

    @Test
    void 담은_순서대로_내려준다() throws Exception {
        Long first = skuWithStock(10);
        Long second = skuWithStock(10);
        Long third = skuWithStock(10);
        add(second, 1);
        add(third, 1);
        add(first, 1);
        add(second, 1);

        cart().andExpect(jsonPath("$.items[*].skuId")
                .value(contains(second.intValue(), third.intValue(), first.intValue())));
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(get("/api/members/me/cart")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/members/me/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content(body(1L, 1))).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/members/me/cart/items/{id}", 1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/members/me/cart/items/{id}", 1)).andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴한_회원의_토큰이면_404() throws Exception {
        Long skuId = skuWithStock(10);
        memberService.withdraw(memberId, "password1");

        cart().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        add(skuId, 1).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        changeQuantity(token(), 1L, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        remove(token(), 1L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }
}
