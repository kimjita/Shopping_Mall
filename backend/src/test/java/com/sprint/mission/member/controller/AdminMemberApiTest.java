package com.sprint.mission.member.controller;

import static org.hamcrest.Matchers.contains;
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
import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.service.OrderService;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class AdminMemberApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    PointService pointService;

    @Autowired
    AddressService addressService;

    @Autowired
    CartService cartService;

    @Autowired
    OrderService orderService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private Long newMember(String email, String name) {
        return memberService.signup(email, "password1", name, "01012345678");
    }

    private ResultActions list(String token, String... params) throws Exception {
        var request = get("/api/admin/members").header(HttpHeaders.AUTHORIZATION, token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions detail(String token, Object memberId) throws Exception {
        return mockMvc.perform(get("/api/admin/members/{memberId}", memberId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private JsonNode parse(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** 새 주문을 만든다. 회원마다 결제 대기 주문은 하나뿐이라 호출할 때마다 앞 주문이 상태를 바꿔 두어야 한다. */
    private void placeOrder(Long memberId, String productName, String status) {
        Long addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        Long brandId = brandService.create(new BrandRequest("브랜드" + productName, null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, productName,
                null, "설명이다.", null, 10_000, List.of("https://img.example.com/a.jpg"),
                List.of(new SkuRequest("기본", 0, 100))));
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartService.add(memberId, new CartAddRequest(skuId, 1));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, memberId, skuId);
        String orderNumber = orderService
                .create(memberId, new OrderCreateRequest(List.of(lineId), null, 0, addressId)).orderNumber();
        jdbcTemplate.update("update orders set status = ? where order_number = ?", status, orderNumber);
    }

    @Test
    void 회원을_최근_가입_순으로_보고_탈퇴_회원도_포함한다() throws Exception {
        Long first = newMember("first@mall.com", "첫째");
        Long second = newMember("second@mall.com", "둘째");
        Long third = newMember("third@mall.com", "셋째");
        memberService.withdraw(second, "password1");

        list(admin(), "size", "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(
                        third.intValue(), second.intValue(), first.intValue())))
                .andExpect(jsonPath("$.items[0].email").value("third@mall.com"))
                .andExpect(jsonPath("$.items[0].name").value("셋째"))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[0].grade").value("BABY"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].createdAt").isString())
                .andExpect(jsonPath("$.items[0].withdrawnAt").doesNotExist())
                .andExpect(jsonPath("$.items[1].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.items[1].withdrawnAt").isString())
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andDo(document("admin-member/list",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("keyword").optional()
                                        .description("검색어. 앞뒤 공백을 지운 1~50자이며 이메일과 이름에서 대소문자 구분 없이 찾는다"),
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY)
                                        .description("회원. 최근 가입한 회원이 먼저다"),
                                fieldWithPath("items[].id").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("items[].email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("items[].name").type(JsonFieldType.STRING).description("이름"),
                                fieldWithPath("items[].role").type(JsonFieldType.STRING)
                                        .description("권한 (`USER`, `ADMIN`)"),
                                fieldWithPath("items[].grade").type(JsonFieldType.STRING)
                                        .description("회원 등급 (`BABY`, `PINK`, `GREEN`, `BLACK`, `GOLD`)"),
                                fieldWithPath("items[].status").type(JsonFieldType.STRING)
                                        .description("회원 상태 (`ACTIVE`, `WITHDRAWN`)"),
                                fieldWithPath("items[].createdAt").type(JsonFieldType.STRING).description("가입 시각"),
                                fieldWithPath("items[].withdrawnAt").type(JsonFieldType.STRING).optional()
                                        .description("탈퇴 시각. 탈퇴하지 않았으면 `null`이다"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`"))));

        JsonNode page1 = parse(list(admin(), "size", "2")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").value(second.toString())));
        list(admin(), "size", "2", "cursor", page1.get("nextCursor").asText())
                .andExpect(jsonPath("$.items[*].id").value(contains(first.intValue())))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void 이메일이나_이름으로_찾는다() throws Exception {
        Long kim = newMember("kim@mall.com", "김민지");
        Long lee = newMember("lee.minji@mall.com", "Lee");
        Long percent = newMember("p@mall.com", "100% 회원");
        Long underscore = newMember("u@mall.com", "a_b");
        newMember("axb@mall.com", "엑스");

        list(admin(), "keyword", "민지").andExpect(jsonPath("$.items[*].id").value(contains(kim.intValue())));
        // 이메일과 이름 중 한쪽만 맞아도 찾는다.
        list(admin(), "keyword", "mall.com").andExpect(jsonPath("$.items.length()").value(5));
        list(admin(), "keyword", "MINJI").andExpect(jsonPath("$.items[*].id").value(contains(lee.intValue())));
        list(admin(), "keyword", "KIM@MALL").andExpect(jsonPath("$.items[*].id").value(contains(kim.intValue())));
        list(admin(), "keyword", "lee").andExpect(jsonPath("$.items[*].id").value(contains(lee.intValue())));
        list(admin(), "keyword", " 김민지 ").andExpect(jsonPath("$.items[*].id").value(contains(kim.intValue())));
        // %와 _는 와일드카드가 아니라 글자 그대로 찾는다.
        list(admin(), "keyword", "%").andExpect(jsonPath("$.items[*].id").value(contains(percent.intValue())));
        list(admin(), "keyword", "a_b").andExpect(jsonPath("$.items[*].id").value(contains(underscore.intValue())));
        list(admin(), "keyword", "_").andExpect(jsonPath("$.items[*].id").value(contains(underscore.intValue())));
        list(admin(), "keyword", "없는회원").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 잘못된_검색어_커서_size면_400() throws Exception {
        list(admin(), "cursor", "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list(admin(), "cursor", "0").andExpect(status().isBadRequest());
        list(admin(), "cursor", "9999999999999999999").andExpect(status().isBadRequest());
        list(admin(), "size", "0").andExpect(status().isBadRequest());
        list(admin(), "size", "51").andExpect(status().isBadRequest());
        list(admin(), "keyword", " ").andExpect(status().isBadRequest());
        list(admin(), "keyword", "a".repeat(51)).andExpect(status().isBadRequest());
        list(admin(), "keyword", "a\u0001b").andExpect(status().isBadRequest());
    }

    @Test
    void 회원_상세에_적립금과_주문_수가_있다() throws Exception {
        Long buyer = newMember("buyer@mall.com", "구매자");
        memberService.updateSkinProfile(buyer, SkinType.OILY, SkinTone.COOL,
                List.of(SkinConcern.PORE, SkinConcern.BLEMISH));
        pointService.grant(buyer, 3_000, "이벤트");
        placeOrder(buyer, "토너", "CANCELED");
        placeOrder(buyer, "에센스", "PAID");
        Long other = newMember("other@mall.com", "다른사람");
        placeOrder(other, "크림", "PAID");

        detail(admin(), buyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(buyer))
                .andExpect(jsonPath("$.email").value("buyer@mall.com"))
                .andExpect(jsonPath("$.name").value("구매자"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.grade").value("BABY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.skinType").value("OILY"))
                .andExpect(jsonPath("$.skinTone").value("COOL"))
                .andExpect(jsonPath("$.skinConcerns").value(contains("BLEMISH", "PORE")))
                .andExpect(jsonPath("$.pointBalance").value(3_000))
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.withdrawnAt").doesNotExist())
                .andDo(document("admin-member/detail",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("memberId").description("회원 ID")),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("이름"),
                                fieldWithPath("phone").type(JsonFieldType.STRING).description("연락처"),
                                fieldWithPath("role").type(JsonFieldType.STRING).description("권한 (`USER`, `ADMIN`)"),
                                fieldWithPath("grade").type(JsonFieldType.STRING)
                                        .description("회원 등급 (`BABY`, `PINK`, `GREEN`, `BLACK`, `GOLD`)"),
                                fieldWithPath("status").type(JsonFieldType.STRING)
                                        .description("회원 상태 (`ACTIVE`, `WITHDRAWN`)"),
                                fieldWithPath("skinType").type(JsonFieldType.STRING).optional()
                                        .description("피부 타입. 저장하지 않았으면 `null`이다"),
                                fieldWithPath("skinTone").type(JsonFieldType.STRING).optional()
                                        .description("피부 톤. 저장하지 않았으면 `null`이다"),
                                fieldWithPath("skinConcerns").type(JsonFieldType.ARRAY)
                                        .description("피부 고민. 없으면 빈 배열이다"),
                                fieldWithPath("pointBalance").type(JsonFieldType.NUMBER)
                                        .description("적립금 잔액 (원)"),
                                fieldWithPath("orderCount").type(JsonFieldType.NUMBER)
                                        .description("주문 수. 취소된 주문을 포함한 모든 상태의 주문이다"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("가입 시각"),
                                fieldWithPath("withdrawnAt").type(JsonFieldType.STRING).optional()
                                        .description("탈퇴 시각. 탈퇴하지 않았으면 `null`이다"))));

        // 피부 프로필이 없고 주문이 없는 회원도 읽힌다.
        Long empty = newMember("empty@mall.com", "빈회원");
        detail(admin(), empty).andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").doesNotExist())
                .andExpect(jsonPath("$.skinConcerns.length()").value(0))
                .andExpect(jsonPath("$.pointBalance").value(0))
                .andExpect(jsonPath("$.orderCount").value(0));
        // 탈퇴 회원은 404가 아니라 그대로 보인다.
        memberService.withdraw(empty, "password1");
        detail(admin(), empty).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.withdrawnAt").isString());
    }

    @Test
    void 없는_회원이면_404() throws Exception {
        detail(admin(), 999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 일반_회원이면_403() throws Exception {
        list(bearer(2L, Role.USER)).andExpect(status().isForbidden());
        detail(bearer(2L, Role.USER), 2L).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/admin/members")).andExpect(status().isUnauthorized());
    }
}
