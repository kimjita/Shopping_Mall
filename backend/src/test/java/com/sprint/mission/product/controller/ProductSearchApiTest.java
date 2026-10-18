package com.sprint.mission.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

class ProductSearchApiTest extends ApiTest {

    private static final List<String> IMAGES = List.of("https://img.example.com/main.jpg",
            "https://img.example.com/sub.jpg");

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
    Long creamId;
    Long cushionId;

    @BeforeEach
    void setUp() {
        roundLab = brandService.create(new BrandRequest("라운드랩", null)).id();
        anua = brandService.create(new BrandRequest("아누아", null)).id();
        tonerId = categoryId("토너");
        creamId = categoryId("크림");
        cushionId = categoryId("쿠션");
    }

    private Long categoryId(String name) {
        return jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
    }

    private Long register(Long brandId, Long categoryId, String name, long basePrice, int stock) {
        return adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, basePrice, IMAGES, List.of(new SkuRequest("기본", 0, stock))));
    }

    private Long register(String name, long basePrice) {
        return register(roundLab, tonerId, name, basePrice, 10);
    }

    private void sale(Long productId, int rate, long startsInSeconds, long endsInSeconds) {
        Instant now = Instant.now();
        adminProductService.startSale(productId, new SaleRequest(rate,
                now.plusSeconds(startsInSeconds), now.plusSeconds(endsInSeconds)));
    }

    private void activeSale(Long productId, int rate) {
        sale(productId, rate, -3_600, 3_600);
    }

    private MockHttpServletRequestBuilder list(String... paramPairs) {
        MockHttpServletRequestBuilder request = get("/api/products");
        for (int i = 0; i < paramPairs.length; i += 2) {
            request.param(paramPairs[i], paramPairs[i + 1]);
        }
        return request;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    @Test
    void 비회원이_신상품순_목록을_본다() throws Exception {
        register("1025 독도 토너", 12_900);
        Long second = register("자작나무 수분 크림", 20_000);
        Long third = register("어성초 진정 토너", 18_000);
        activeSale(third, 10);

        mockMvc.perform(list("size", "2", "sort", "NEWEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(third.intValue(), second.intValue())))
                .andExpect(jsonPath("$.items[0].name").value("어성초 진정 토너"))
                .andExpect(jsonPath("$.items[0].brandName").value("라운드랩"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.items[0].price").value(18_000))
                .andExpect(jsonPath("$.items[0].salePrice").value(16_200))
                .andExpect(jsonPath("$.items[0].saleRate").value(10))
                .andExpect(jsonPath("$.items[0].soldOut").value(false))
                .andExpect(jsonPath("$.items[1].saleRate").value(nullValue()))
                .andExpect(jsonPath("$.items[1].salePrice").value(20_000))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                .andDo(document("product/search",
                        queryParameters(
                                parameterWithName("keyword").optional()
                                        .description("검색어. 앞뒤 공백을 지운 1~50자이며 상품명과 브랜드명에서 대소문자 구분 없이 찾는다"),
                                parameterWithName("categoryId").optional()
                                        .description("카테고리 ID. 어느 단계든 받고, 그 아래 가장 아래 단계 카테고리 전체로 거른다"),
                                parameterWithName("brandIds").optional()
                                        .description("브랜드 ID 목록. 최대 20개이며 `1,2`처럼 콤마로 구분하거나 파라미터를 반복한다"),
                                parameterWithName("minPrice").optional()
                                        .description("현재 판매가 하한 (원). 0 이상이다"),
                                parameterWithName("maxPrice").optional()
                                        .description("현재 판매가 상한 (원). `minPrice` 이상이어야 한다"),
                                parameterWithName("onSale").optional()
                                        .description("`true`면 진행 중인 세일 상품만 보인다. 없거나 `false`면 거르지 않는다"),
                                parameterWithName("sort").optional()
                                        .description("정렬. `NEWEST`(기본), `PRICE_ASC`, `PRICE_DESC`"),
                                parameterWithName("cursor").optional()
                                        .description("이전 응답의 `nextCursor`. 같은 `sort`로만 쓸 수 있다"),
                                parameterWithName("size").optional()
                                        .description("한 번에 가져올 개수. 기본 20이고 1~50이다")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("상품 목록"),
                                fieldWithPath("items[].id").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].name").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("items[].brandName").type(JsonFieldType.STRING)
                                        .description("브랜드 이름"),
                                fieldWithPath("items[].thumbnailUrl").type(JsonFieldType.STRING)
                                        .description("대표 이미지 URL"),
                                fieldWithPath("items[].price").type(JsonFieldType.NUMBER)
                                        .description("정가 (원). 옵션 중 가장 싼 정가다"),
                                fieldWithPath("items[].salePrice").type(JsonFieldType.NUMBER)
                                        .description("현재 판매가 (원). 세일 중이면 할인율을 적용해 10원 단위로 내린 값이고, 아니면 정가와 같다"),
                                fieldWithPath("items[].saleRate").type(JsonFieldType.NUMBER).optional()
                                        .description("진행 중인 세일의 할인율 (%). 진행 중인 세일이 없으면 `null`"),
                                fieldWithPath("items[].soldOut").type(JsonFieldType.BOOLEAN)
                                        .description("품절 여부. 모든 옵션이 품절이면 `true`다"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지를 읽는 커서. 마지막 페이지면 `null`"))));
    }

    @Test
    void 검색어는_상품명과_브랜드명에서_찾는다() throws Exception {
        Long dokdo = register(roundLab, tonerId, "1025 독도 토너", 12_900, 10);
        Long birch = register(roundLab, creamId, "자작나무 수분 크림", 20_000, 10);
        register(anua, tonerId, "어성초 진정 토너", 18_000, 10);

        mockMvc.perform(list("keyword", "독도"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(dokdo.intValue())));
        mockMvc.perform(list("keyword", "라운드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(birch.intValue(), dokdo.intValue())));
        mockMvc.perform(list("keyword", "  라운드랩 1025  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(dokdo.intValue())));
        mockMvc.perform(list("keyword", "없는검색어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(empty()))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 퍼센트와_밑줄은_글자_그대로_찾는다() throws Exception {
        Long percent = register("100% 퓨어 오일", 15_000);
        register("1000 오일", 15_000);
        Long underscore = register("a_b 세럼", 15_000);
        register("axb 세럼", 15_000);
        Long backslash = register("c\\d 크림", 15_000);
        register("cd 크림", 15_000);

        mockMvc.perform(list("keyword", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(percent.intValue())));
        mockMvc.perform(list("keyword", "a_b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(underscore.intValue())));
        mockMvc.perform(list("keyword", "c\\d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(backslash.intValue())));
    }

    @Test
    void 상위_카테고리로_하위_상품을_모두_찾는다() throws Exception {
        Long toner = register(roundLab, tonerId, "독도 토너", 12_900, 10);
        Long cream = register(roundLab, creamId, "자작나무 크림", 20_000, 10);
        register(roundLab, cushionId, "글로우 쿠션", 25_000, 10);

        mockMvc.perform(list("categoryId", categoryId("스킨케어").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(cream.intValue(), toner.intValue())));
        mockMvc.perform(list("categoryId", tonerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(toner.intValue())));
    }

    @Test
    void 없는_카테고리는_404() throws Exception {
        mockMvc.perform(list("categoryId", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void 브랜드_여러_개로_거른다() throws Exception {
        Long third = brandService.create(new BrandRequest("토리든", null)).id();
        Long a = register(roundLab, tonerId, "독도 토너", 12_900, 10);
        Long b = register(anua, tonerId, "어성초 토너", 18_000, 10);
        register(third, tonerId, "다이브인 토너", 16_000, 10);

        mockMvc.perform(list("brandIds", roundLab + "," + anua))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(b.intValue(), a.intValue())));
        mockMvc.perform(get("/api/products").param("brandIds", roundLab.toString()).param("brandIds", anua.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(b.intValue(), a.intValue())));
        mockMvc.perform(list("brandIds", third.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void 세일_중인_상품만_거른다() throws Exception {
        Long active = register("세일 중", 10_000);
        Long future = register("세일 예정", 10_000);
        Long expired = register("세일 종료", 10_000);
        register("세일 없음", 10_000);
        activeSale(active, 20);
        sale(future, 20, 3_600, 7_200);
        sale(expired, 20, -7_200, -3_600);

        mockMvc.perform(list("onSale", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(active.intValue())))
                .andExpect(jsonPath("$.items[0].saleRate").value(20));
        mockMvc.perform(list("onSale", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4));
        mockMvc.perform(list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[?(@.name == '세일 예정')].saleRate").value(contains(nullValue())))
                .andExpect(jsonPath("$.items[?(@.name == '세일 종료')].salePrice").value(contains(10_000)));
    }

    @Test
    void 가격대는_세일가_기준이다() throws Exception {
        Long discounted = register("반값 세일", 20_000);
        Long regular = register("정가 상품", 20_000);
        activeSale(discounted, 50);

        mockMvc.perform(list("maxPrice", "15000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(discounted.intValue())));
        mockMvc.perform(list("minPrice", "11000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(regular.intValue())));
        mockMvc.perform(list("minPrice", "10000", "maxPrice", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(discounted.intValue())));
    }

    @Test
    void 가격_낮은순은_세일가로_정렬한다() throws Exception {
        Long discounted = register("90% 세일", 30_000);
        Long cheap = register("정가 싼 상품", 5_000);
        activeSale(discounted, 90);

        mockMvc.perform(list("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(discounted.intValue(), cheap.intValue())))
                .andExpect(jsonPath("$.items[0].salePrice").value(3_000));
        mockMvc.perform(list("sort", "PRICE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(cheap.intValue(), discounted.intValue())));
    }

    @Test
    void 목록_세일가와_상세_세일가가_같다() throws Exception {
        Long id = register("15% 세일", 14_900);
        activeSale(id, 15);

        mockMvc.perform(list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].price").value(14_900))
                .andExpect(jsonPath("$.items[0].salePrice").value(12_660))
                .andExpect(jsonPath("$.items[0].saleRate").value(15));
        JsonNode detail = json(mockMvc.perform(get("/api/products/{id}", id)).andReturn());
        long detailMin = Long.MAX_VALUE;
        for (JsonNode sku : detail.get("skus")) {
            detailMin = Math.min(detailMin, sku.get("salePrice").asLong());
        }
        assertThat(detailMin).isEqualTo(12_660);
    }

    @Test
    void 커서로_끝까지_읽으면_중복도_누락도_없다() throws Exception {
        // 가격: 10,000 x3(동률), 5,000, 20,000, 7,000, 30,000(50% 세일 -> 15,000)
        Map<Long, Long> priceById = new HashMap<>();
        for (long price : new long[] {10_000, 10_000, 10_000, 5_000, 20_000, 7_000}) {
            priceById.put(register("상품 " + priceById.size(), price), price);
        }
        Long discounted = register("세일 상품", 30_000);
        activeSale(discounted, 50);
        priceById.put(discounted, 15_000L);
        // 등록 시각 동률 3개
        List<Long> all = new ArrayList<>(priceById.keySet());
        all.sort(Comparator.naturalOrder());
        Instant tied = Instant.now().minus(1, ChronoUnit.DAYS);
        for (Long id : all.subList(0, 3)) {
            jdbcTemplate.update("update product set created_at = ? where id = ?",
                    java.sql.Timestamp.from(tied), id);
        }

        List<Long> newestExpected = jdbcTemplate.queryForList(
                "select id from product order by created_at desc, id desc", Long.class);
        List<Long> ascExpected = all.stream()
                .sorted(Comparator.<Long>comparingLong(priceById::get).thenComparing(Comparator.naturalOrder()))
                .toList();
        List<Long> descExpected = all.stream()
                .sorted(Comparator.<Long>comparingLong(priceById::get).thenComparing(Comparator.naturalOrder())
                        .reversed())
                .toList();

        assertThat(readAll("NEWEST", 3)).containsExactlyElementsOf(newestExpected);
        assertThat(readAll("PRICE_ASC", 3)).containsExactlyElementsOf(ascExpected);
        assertThat(readAll("PRICE_DESC", 3)).containsExactlyElementsOf(descExpected);
        assertThat(readAll("PRICE_ASC", 7)).containsExactlyElementsOf(ascExpected);
        assertThat(readAll("PRICE_ASC", 1)).containsExactlyElementsOf(ascExpected);
    }

    private List<Long> readAll(String sort, int size) throws Exception {
        List<Long> collected = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 20; page++) {
            MockHttpServletRequestBuilder request = list("sort", sort, "size", String.valueOf(size));
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode body = json(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
            collected.addAll(ids(body));
            if (body.get("nextCursor").isNull()) {
                assertThat(new HashSet<>(collected)).hasSameSizeAs(collected);
                return collected;
            }
            cursor = body.get("nextCursor").asText();
        }
        throw new AssertionError("커서가 끝나지 않는다.");
    }

    @Test
    void 판매중지_삭제_상품은_목록에_없다() throws Exception {
        Long visible = register("판매 중", 10_000);
        Long stopped = register("판매 중지", 10_000);
        Long deleted = register("삭제", 10_000);
        jdbcTemplate.update("update product set status = ? where id = ?", ProductStatus.STOPPED.name(), stopped);
        jdbcTemplate.update("update product set status = ? where id = ?", ProductStatus.DELETED.name(), deleted);

        mockMvc.perform(list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(visible.intValue())));
        mockMvc.perform(list("keyword", "삭제"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(empty()));
    }

    @Test
    void 품절_상품도_목록에_나오고_soldOut이_true다() throws Exception {
        Long soldOut = register(roundLab, tonerId, "품절 토너", 10_000, 0);
        Long inStock = register(roundLab, tonerId, "재고 토너", 10_000, 5);

        mockMvc.perform(list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + soldOut + ")].soldOut").value(contains(true)))
                .andExpect(jsonPath("$.items[?(@.id == " + inStock + ")].soldOut").value(contains(false)));
    }

    @Test
    void 옵션_하나라도_재고가_있으면_품절이_아니다() throws Exception {
        Long id = adminProductService.create(new ProductCreateRequest(roundLab, tonerId, "옵션 토너", null,
                "설명이다.", null, 10_000, IMAGES,
                List.of(new SkuRequest("A", 0, 0), new SkuRequest("B", 1_000, 3))));

        mockMvc.perform(list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].price").value(10_000))
                .andExpect(jsonPath("$.items[0].soldOut").value(false));
    }

    @Test
    void 목록_조회는_쿼리를_한_번만_실행한다() throws Exception {
        for (int i = 0; i < 25; i++) {
            Long id = register("상품 " + i, 10_000 + i * 100L);
            if (i % 2 == 0) {
                activeSale(id, 10);
            }
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // 카테고리 캐시를 미리 데운다.
        mockMvc.perform(list("categoryId", categoryId("스킨케어").toString())).andExpect(status().isOk());

        long[] counts = new long[2];
        int[] sizes = {3, 20};
        for (int i = 0; i < sizes.length; i++) {
            statistics.clear();
            mockMvc.perform(list("categoryId", categoryId("스킨케어").toString(), "sort", "PRICE_ASC",
                            "size", String.valueOf(sizes[i])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(sizes[i]));
            counts[i] = statistics.getPrepareStatementCount();
        }

        assertThat(counts[0]).isEqualTo(counts[1]);
        assertThat(counts[0]).isEqualTo(1);
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        String twentyOne = String.join(",", java.util.Collections.nCopies(21, "1"));
        String[][] invalid = {
                {"size", "0"},
                {"size", "51"},
                {"minPrice", "2000", "maxPrice", "1000"},
                {"minPrice", "-1"},
                {"brandIds", twentyOne},
                {"sort", "POPULAR"},
                {"keyword", "가".repeat(51)},
                {"keyword", "   "},
                {"size", "abc"},
                {"cursor", "깨진커서"},
        };

        for (String[] params : invalid) {
            mockMvc.perform(list(params))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 정렬이_다른_커서는_400() throws Exception {
        register("상품 1", 10_000);
        register("상품 2", 10_000);
        JsonNode page = json(mockMvc.perform(list("sort", "PRICE_ASC", "size", "1")).andReturn());
        String cursor = page.get("nextCursor").asText();

        mockMvc.perform(list("sort", "NEWEST", "cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(list("sort", "PRICE_DESC", "cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 한도_경계값은_받는다() throws Exception {
        register("상품", 10_000);
        String twenty = String.join(",", java.util.Collections.nCopies(20, roundLab.toString()));

        mockMvc.perform(list("size", "1")).andExpect(status().isOk());
        mockMvc.perform(list("size", "50")).andExpect(status().isOk());
        mockMvc.perform(list("brandIds", twenty)).andExpect(status().isOk());
        mockMvc.perform(list("keyword", "가".repeat(50))).andExpect(status().isOk());
        mockMvc.perform(list("minPrice", "0", "maxPrice", "0")).andExpect(status().isOk());
        mockMvc.perform(list("minPrice", "10000", "maxPrice", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }
}
