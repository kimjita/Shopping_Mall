package com.sprint.mission.review;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.service.OrderService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 리뷰 테스트용 상품·회원·주문상품을 만든다. 주문은 서비스로 만든 뒤 SQL로 원하는 상태로 옮긴다. */
@Component
public class ReviewFixtures {

    private final MemberService memberService;
    private final AddressService addressService;
    private final CartService cartService;
    private final OrderService orderService;
    private final BrandService brandService;
    private final AdminProductService adminProductService;
    private final JdbcTemplate jdbcTemplate;

    public ReviewFixtures(MemberService memberService, AddressService addressService, CartService cartService,
            OrderService orderService, BrandService brandService, AdminProductService adminProductService,
            JdbcTemplate jdbcTemplate) {
        this.memberService = memberService;
        this.addressService = addressService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.brandService = brandService;
        this.adminProductService = adminProductService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 옵션 하나(재고 1,000)인 20,000원 상품을 만든다. 같은 브랜드 이름은 한 번만 만들어진다고 가정하지 않으므로 테스트당 한 번만 부른다. */
    public Long product(String name) {
        Long brandId = jdbcTemplate.query("select id from brand limit 1", rs -> rs.next() ? rs.getLong(1) : null);
        if (brandId == null) {
            brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        }
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        return adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null, "설명이다.", null,
                20_000L, List.of("https://img.example.com/main.jpg"), List.of(new SkuRequest("기본", 0, 1_000))));
    }

    public Long member(String email) {
        return memberService.signup(email, "password1", "홍길동", "01012345678");
    }

    /** 회원이 상품 하나를 주문하게 하고, 주문을 주어진 상태로 옮긴 뒤 주문상품 id를 돌려준다. */
    public Long orderItem(Long memberId, Long productId, String orderStatus) {
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        Long addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        cartService.add(memberId, new CartAddRequest(skuId, 1));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, memberId, skuId);
        Long orderId = jdbcTemplate.queryForObject("select id from orders where order_number = ?", Long.class,
                orderService.create(memberId, new OrderCreateRequest(List.of(lineId), null, 0, addressId))
                        .orderNumber());
        jdbcTemplate.update("update orders set status = ?, delivered_at = now(), "
                + "confirmed_at = case when ? = 'CONFIRMED' then now() end where id = ?",
                orderStatus, orderStatus, orderId);
        return jdbcTemplate.queryForObject("select id from order_item where order_id = ?", Long.class, orderId);
    }
}
