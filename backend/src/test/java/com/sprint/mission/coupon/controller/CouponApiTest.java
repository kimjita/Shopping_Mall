package com.sprint.mission.coupon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.coupon.service.AdminCouponService;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.member.domain.Grade;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.ApiTest;
import java.time.Duration;
import java.time.Instant;
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

class CouponApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    private static final HeaderDescriptor ADMIN_AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    AdminCouponService adminCouponService;

    @Autowired
    CouponService couponService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private String user() {
        return bearer(memberId, Role.USER);
    }

    private ResultActions createCoupon(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/coupons")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions createAsAdmin(String body) throws Exception {
        return createCoupon(bearer(1L, Role.ADMIN), body);
    }

    private ResultActions download(Long couponId) throws Exception {
        return mockMvc.perform(post("/api/members/me/coupons/{couponId}/download", couponId)
                .header(HttpHeaders.AUTHORIZATION, user()));
    }

    /** 잘못된 정의를 만들어 보내기 위한 쿠폰 JSON이다. 이름은 "가을 쿠폰", 수량은 100, 최소 주문금액은 10,000원이다. */
    private String couponJson(String discountType, String discountValue, String maxDiscount, String issueStart,
            String issueEnd, String useStart, String useEnd) {
        return """
                {"name":"가을 쿠폰","discountType":"%s","discountValue":%s,"maxDiscount":%s,
                "minOrderAmount":10000,"totalQuantity":100,"requiredGrade":null,
                "issueStartsAt":"%s","issueEndsAt":"%s","useStartsAt":"%s","useEndsAt":"%s"}"""
                .formatted(discountType, discountValue, maxDiscount, issueStart, issueEnd, useStart, useEnd);
    }

    /** 요청과 응답이 함께 가진 필드다. 응답에는 id·발급 수량·등록 시각이 더 있다. */
    private static FieldDescriptor[] couponFields(String prefix, boolean response) {
        java.util.List<FieldDescriptor> fields = new java.util.ArrayList<>(java.util.List.of(
                fieldWithPath(prefix + "name").type(JsonFieldType.STRING).description("쿠폰 이름 (1~50자)"),
                fieldWithPath(prefix + "discountType").type(JsonFieldType.STRING)
                        .description("할인 방식 (`FIXED` 정액, `RATE` 정률)"),
                fieldWithPath(prefix + "discountValue").type(JsonFieldType.NUMBER)
                        .description("할인 값. 정액이면 100~1,000,000원, 정률이면 1~90%"),
                fieldWithPath(prefix + "maxDiscount").type(JsonFieldType.NUMBER).optional()
                        .description("정률 쿠폰의 최대 할인 금액 (원). 정률이면 필수, 정액이면 `null`"),
                fieldWithPath(prefix + "minOrderAmount").type(JsonFieldType.NUMBER)
                        .description("최소 주문금액 (원, 0 이상). 세일가 합계가 이 값 이상이어야 쓸 수 있다"),
                fieldWithPath(prefix + "totalQuantity").type(JsonFieldType.NUMBER)
                        .description("총 발급 수량 (1~1,000,000)"),
                fieldWithPath(prefix + "requiredGrade").type(JsonFieldType.STRING).optional()
                        .description("받을 수 있는 최소 등급. `null`이면 누구나 받는다"),
                fieldWithPath(prefix + "issueStartsAt").type(JsonFieldType.STRING)
                        .description("발급 시작 시각 (ISO-8601, 포함)"),
                fieldWithPath(prefix + "issueEndsAt").type(JsonFieldType.STRING)
                        .description("발급 종료 시각 (ISO-8601, 미포함)"),
                fieldWithPath(prefix + "useStartsAt").type(JsonFieldType.STRING)
                        .description("사용 시작 시각 (ISO-8601, 포함)"),
                fieldWithPath(prefix + "useEndsAt").type(JsonFieldType.STRING)
                        .description("사용 종료 시각 (ISO-8601, 미포함)")));
        if (response) {
            fields.add(fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("쿠폰 ID"));
            fields.add(fieldWithPath(prefix + "issuedQuantity").type(JsonFieldType.NUMBER)
                    .description("지금까지 발급된 수량"));
            fields.add(fieldWithPath(prefix + "createdAt").type(JsonFieldType.STRING)
                    .description("등록 시각 (ISO-8601)"));
        }
        return fields.toArray(new FieldDescriptor[0]);
    }

    @Test
    void 관리자가_쿠폰을_만든다() throws Exception {
        Instant now = Instant.now();
        String body = """
                {"name":"가을 맞이 10%% 쿠폰","discountType":"RATE","discountValue":10,"maxDiscount":5000,
                "minOrderAmount":20000,"totalQuantity":100,"requiredGrade":"PINK",
                "issueStartsAt":"%s","issueEndsAt":"%s","useStartsAt":"%s","useEndsAt":"%s"}"""
                .formatted(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(7)),
                        now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)));

        createAsAdmin(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("가을 맞이 10% 쿠폰"))
                .andExpect(jsonPath("$.discountType").value("RATE"))
                .andExpect(jsonPath("$.totalQuantity").value(100))
                .andExpect(jsonPath("$.issuedQuantity").value(0))
                .andExpect(jsonPath("$.requiredGrade").value("PINK"))
                .andDo(document("admin-coupon/create",
                        requestHeaders(ADMIN_AUTHORIZATION),
                        requestFields(couponFields("", false)),
                        responseFields(couponFields("", true))));

        mockMvc.perform(get("/api/admin/coupons").header(HttpHeaders.AUTHORIZATION, bearer(1L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andDo(document("admin-coupon/list",
                        requestHeaders(ADMIN_AUTHORIZATION),
                        responseFields(couponFields("[].", true))));
    }

    @Test
    void 받을_수_있는_쿠폰을_보고_받는다() throws Exception {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 100, null)).id();

        mockMvc.perform(get("/api/members/me/coupons/downloadable").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].couponId").value(couponId))
                .andExpect(jsonPath("$[0].eligible").value(true))
                .andExpect(jsonPath("$[0].remainingQuantity").value(100))
                .andDo(document("coupon/downloadable",
                        requestHeaders(AUTHORIZATION),
                        responseFields(
                                fieldWithPath("[].couponId").type(JsonFieldType.NUMBER).description("쿠폰 ID"),
                                fieldWithPath("[].name").type(JsonFieldType.STRING).description("쿠폰 이름"),
                                fieldWithPath("[].discountType").type(JsonFieldType.STRING)
                                        .description("할인 방식 (`FIXED` 정액, `RATE` 정률)"),
                                fieldWithPath("[].discountValue").type(JsonFieldType.NUMBER)
                                        .description("할인 값. 정액이면 원, 정률이면 퍼센트"),
                                fieldWithPath("[].maxDiscount").type(JsonFieldType.NUMBER).optional()
                                        .description("정률 쿠폰의 최대 할인 금액 (원). 정액 쿠폰이면 `null`"),
                                fieldWithPath("[].minOrderAmount").type(JsonFieldType.NUMBER)
                                        .description("최소 주문금액 (원)"),
                                fieldWithPath("[].requiredGrade").type(JsonFieldType.STRING).optional()
                                        .description("받을 수 있는 최소 등급. `null`이면 누구나 받는다"),
                                fieldWithPath("[].eligible").type(JsonFieldType.BOOLEAN)
                                        .description("내 등급으로 받을 수 있는지 여부. `false`면 받기를 시도해도 거절된다"),
                                fieldWithPath("[].remainingQuantity").type(JsonFieldType.NUMBER)
                                        .description("남은 수량"),
                                fieldWithPath("[].issueEndsAt").type(JsonFieldType.STRING)
                                        .description("발급 종료 시각 (ISO-8601, 미포함)"),
                                fieldWithPath("[].useStartsAt").type(JsonFieldType.STRING)
                                        .description("사용 시작 시각 (ISO-8601, 포함)"),
                                fieldWithPath("[].useEndsAt").type(JsonFieldType.STRING)
                                        .description("사용 종료 시각 (ISO-8601, 미포함)"))));

        download(couponId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.couponId").value(couponId))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andDo(document("coupon/download",
                        pathParameters(parameterWithName("couponId").description("받을 쿠폰 ID")),
                        requestHeaders(AUTHORIZATION),
                        responseFields(myCouponFields("", false))));

        // 받은 쿠폰은 받을 수 있는 목록에서 빠진다.
        mockMvc.perform(get("/api/members/me/coupons/downloadable").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        download(couponId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_ISSUED"));

        mockMvc.perform(get("/api/members/me/coupons").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].couponId").value(couponId))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andDo(document("coupon/my",
                        requestHeaders(AUTHORIZATION),
                        responseFields(myCouponFields("[].", true))));
    }

    private static FieldDescriptor[] myCouponFields(String prefix, boolean list) {
        return new FieldDescriptor[] {
                fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("내 쿠폰 ID (주문에 쓰는 값)"),
                fieldWithPath(prefix + "couponId").type(JsonFieldType.NUMBER).description("쿠폰 ID"),
                fieldWithPath(prefix + "name").type(JsonFieldType.STRING).description("쿠폰 이름"),
                fieldWithPath(prefix + "discountType").type(JsonFieldType.STRING)
                        .description("할인 방식 (`FIXED` 정액, `RATE` 정률)"),
                fieldWithPath(prefix + "discountValue").type(JsonFieldType.NUMBER)
                        .description("할인 값. 정액이면 원, 정률이면 퍼센트"),
                fieldWithPath(prefix + "maxDiscount").type(JsonFieldType.NUMBER).optional()
                        .description("정률 쿠폰의 최대 할인 금액 (원). 정액 쿠폰이면 `null`"),
                fieldWithPath(prefix + "minOrderAmount").type(JsonFieldType.NUMBER).description("최소 주문금액 (원)"),
                fieldWithPath(prefix + "status").type(JsonFieldType.STRING)
                        .description("상태 (`AVAILABLE` 사용 가능, `USED` 사용함, `EXPIRED` 사용 기간 종료)"),
                fieldWithPath(prefix + "useStartsAt").type(JsonFieldType.STRING)
                        .description("사용 시작 시각 (ISO-8601, 포함)"),
                fieldWithPath(prefix + "useEndsAt").type(JsonFieldType.STRING)
                        .description("사용 종료 시각 (ISO-8601, 미포함)"),
                fieldWithPath(prefix + "usedAt").type(JsonFieldType.STRING).optional()
                        .description("사용한 시각 (ISO-8601). 사용하지 않았으면 `null`"),
                fieldWithPath(prefix + "issuedAt").type(JsonFieldType.STRING).description("받은 시각 (ISO-8601)")
        };
    }

    @Test
    void 내_쿠폰은_최신순이다() throws Exception {
        Long first = adminCouponService.create(CouponFixtures.fixed("먼저", 10, null)).id();
        Long second = adminCouponService.create(CouponFixtures.fixed("나중", 10, null)).id();
        couponService.download(memberId, first);
        couponService.download(memberId, second);

        mockMvc.perform(get("/api/members/me/coupons").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].couponId").value(second))
                .andExpect(jsonPath("$[1].couponId").value(first));
    }

    @Test
    void 등급_전용_쿠폰은_낮은_등급이_받을_수_없다() throws Exception {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("PINK 전용", 10, Grade.PINK)).id();

        mockMvc.perform(get("/api/members/me/coupons/downloadable").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requiredGrade").value("PINK"))
                .andExpect(jsonPath("$[0].eligible").value(false));

        download(couponId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_ISSUABLE"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from member_coupon", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select issued_quantity from coupon where id = ?", Integer.class, couponId)).isZero();
    }

    @Test
    void 발급_기간이_아니면_400() throws Exception {
        Instant now = Instant.now();
        Long notYet = adminCouponService.create(CouponFixtures.fixed("예정", 10, null,
                now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)),
                now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)))).id();
        Long ended = adminCouponService.create(CouponFixtures.fixed("종료", 10, null,
                now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)),
                now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(30)))).id();

        download(notYet).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_ISSUABLE"));
        download(ended).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_ISSUABLE"));
        mockMvc.perform(get("/api/members/me/coupons/downloadable").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 없는_쿠폰을_받으면_404() throws Exception {
        download(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    @Test
    void 사용_기간이_지난_쿠폰은_EXPIRED로_보인다() throws Exception {
        Instant now = Instant.now();
        // 발급 기간은 지금이지만 사용 기간은 이미 끝난 쿠폰이다.
        Long expiredId = adminCouponService.create(CouponFixtures.fixed("만료", 10, null,
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)),
                now.minus(Duration.ofDays(10)), now.minus(Duration.ofHours(1)))).id();
        Long liveId = adminCouponService.create(CouponFixtures.fixed("유효", 10, null)).id();
        Long usedId = adminCouponService.create(CouponFixtures.fixed("사용함", 10, null)).id();
        couponService.download(memberId, expiredId);
        couponService.download(memberId, liveId);
        couponService.download(memberId, usedId);
        jdbcTemplate.update("update member_coupon set status = 'USED', used_at = now() where coupon_id = ?", usedId);
        // 사용한 쿠폰은 기간이 지나도 USED로 남는다.
        jdbcTemplate.update("update coupon set use_ends_at = now() - interval '1 hour', "
                + "use_starts_at = now() - interval '2 days' where id = ?", usedId);

        mockMvc.perform(get("/api/members/me/coupons").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("사용함"))
                .andExpect(jsonPath("$[0].status").value("USED"))
                .andExpect(jsonPath("$[0].usedAt").isString())
                .andExpect(jsonPath("$[1].name").value("유효"))
                .andExpect(jsonPath("$[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[2].name").value("만료"))
                .andExpect(jsonPath("$[2].status").value("EXPIRED"));

        // EXPIRED는 화면용 상태라 DB에는 AVAILABLE로 남는다.
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupon where coupon_id = ?", String.class, expiredId))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void 잘못된_쿠폰_정의는_400() throws Exception {
        Instant now = Instant.now();
        String is = now.minus(Duration.ofDays(1)).toString();
        String ie = now.plus(Duration.ofDays(1)).toString();
        String us = now.minus(Duration.ofDays(1)).toString();
        String ue = now.plus(Duration.ofDays(30)).toString();

        // 정률 95%
        createAsAdmin(couponJson("RATE", "95", "5000", is, ie, us, ue))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // 정률인데 최대 할인 금액이 없다
        createAsAdmin(couponJson("RATE", "10", "null", is, ie, us, ue))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // 정액인데 최대 할인 금액이 있다
        createAsAdmin(couponJson("FIXED", "3000", "5000", is, ie, us, ue))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // 정액 99원
        createAsAdmin(couponJson("FIXED", "99", "null", is, ie, us, ue))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // 발급 종료가 시작보다 빠르다
        createAsAdmin(couponJson("FIXED", "3000", "null", ie, is, us, ue))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // 사용 종료가 시작보다 빠르다
        createAsAdmin(couponJson("FIXED", "3000", "null", is, ie, ue, us))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // 필수 값 누락, 이름 51자, 수량 0, 알 수 없는 할인 방식
        createAsAdmin("{}").andExpect(status().isBadRequest());
        createAsAdmin(couponJson("FIXED", "3000", "null", is, ie, us, ue).replace("가을 쿠폰", "가".repeat(51)))
                .andExpect(status().isBadRequest());
        createAsAdmin(couponJson("FIXED", "3000", "null", is, ie, us, ue).replace("\"totalQuantity\":100",
                "\"totalQuantity\":0")).andExpect(status().isBadRequest());
        createAsAdmin(couponJson("PERCENT", "3000", "null", is, ie, us, ue)).andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupon", Integer.class)).isZero();
        // 정상 값은 만들어진다.
        createAsAdmin(couponJson("RATE", "90", "5000", is, ie, us, ue)).andExpect(status().isCreated());
    }

    @Test
    void USER는_쿠폰을_만들_수_없다() throws Exception {
        Instant now = Instant.now();
        createCoupon(user(), couponJson("FIXED", "3000", "null", now.minus(Duration.ofDays(1)).toString(),
                now.plus(Duration.ofDays(1)).toString(), now.minus(Duration.ofDays(1)).toString(),
                now.plus(Duration.ofDays(30)).toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/coupons").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupon", Integer.class)).isZero();
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/api/members/me/coupons/downloadable")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/members/me/coupons/{couponId}/download", 1L)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/me/coupons")).andExpect(status().isUnauthorized());
    }
}
