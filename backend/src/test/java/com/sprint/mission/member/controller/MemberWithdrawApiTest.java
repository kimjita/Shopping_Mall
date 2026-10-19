package com.sprint.mission.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.recentview.service.RecentViewService;
import com.sprint.mission.support.ApiTest;
import com.sprint.mission.wishlist.service.WishlistService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** 탈퇴 정책이다: 진행 중 주문이 있으면 거부하고, 탈퇴하면 장바구니·찜·최근 본 상품을 지운다. */
class MemberWithdrawApiTest extends ApiTest {

    @Autowired
    MemberService memberService;

    @Autowired
    AddressService addressService;

    @Autowired
    CartService cartService;

    @Autowired
    WishlistService wishlistService;

    @Autowired
    RecentViewService recentViewService;

    @Autowired
    PointService pointService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;
    Long addressId;
    Long productId;
    Long cartItemId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "1025 독도 토너", null,
                "설명이다.", null, 14_900L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartService.add(memberId, new CartAddRequest(skuId, 1));
        cartItemId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ?", Long.class, memberId);
        wishlistService.add(memberId, productId);
        recentViewService.record(memberId, productId);
    }

    private ResultActions placeOrder(long points) throws Exception {
        return mockMvc.perform(post("/api/members/me/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartItemIds\":[" + cartItemId + "],\"memberCouponId\":null,\"points\":" + points
                        + ",\"addressId\":" + addressId + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions withdraw(String password) throws Exception {
        return mockMvc.perform(delete("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where member_id = ?", Integer.class,
                memberId);
    }

    private String memberStatus() {
        return jdbcTemplate.queryForObject("select status from member where id = ?", String.class, memberId);
    }

    @Test
    void 대기_주문이_있으면_409_MEMBER_HAS_ACTIVE_ORDER() throws Exception {
        placeOrder(0);

        withdraw("password1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_ORDER"));

        assertThat(memberStatus()).isEqualTo("ACTIVE");
        assertThat(count("cart_item")).isEqualTo(1);
        assertThat(count("wishlist")).isEqualTo(1);
        assertThat(count("recent_view")).isEqualTo(1);
    }

    @Test
    void 취소된_주문만_있으면_탈퇴하고_장바구니_찜_최근_본_상품을_지운다() throws Exception {
        pointService.grant(memberId, 5_000, "이벤트");
        placeOrder(1_000);
        jdbcTemplate.update("update orders set status = 'CANCELED', cancel_reason = 'EXPIRED', canceled_at = now() "
                + "where member_id = ?", memberId);
        int pointRows = count("point_history");

        withdraw("password1").andExpect(status().isNoContent());

        assertThat(memberStatus()).isEqualTo("WITHDRAWN");
        assertThat(count("cart_item")).isZero();
        assertThat(count("wishlist")).isZero();
        assertThat(count("recent_view")).isZero();
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("point_history")).isEqualTo(pointRows).isPositive();
    }

    @Test
    void 비밀번호가_틀리면_주문이_있어도_400_PASSWORD_MISMATCH() throws Exception {
        placeOrder(0);

        withdraw("wrong-pass1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        assertThat(memberStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void 다른_회원의_장바구니_찜_최근_본_상품은_그대로_남는다() throws Exception {
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01099998888");
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartService.add(other, new CartAddRequest(skuId, 1));
        wishlistService.add(other, productId);
        recentViewService.record(other, productId);

        withdraw("password1").andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select count(*) from cart_item where member_id = ?", Integer.class,
                other)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from wishlist where member_id = ?", Integer.class,
                other)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from recent_view where member_id = ?", Integer.class,
                other)).isEqualTo(1);
    }
}
