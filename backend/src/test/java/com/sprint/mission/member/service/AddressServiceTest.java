package com.sprint.mission.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.dto.AddressResponse;
import com.sprint.mission.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AddressServiceTest extends IntegrationTest {

    @Autowired
    MemberService memberService;

    @Autowired
    AddressService addressService;

    Long memberId;

    @BeforeEach
    void 회원을_가입시킨다() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private AddressRequest request(String recipient, boolean isDefault) {
        return new AddressRequest(recipient, "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101호", isDefault);
    }

    private List<Boolean> defaults() {
        return addressService.list(memberId).stream().map(AddressResponse::isDefault).toList();
    }

    @Test
    void 첫_배송지는_자동으로_기본이다() {
        AddressResponse created = addressService.create(memberId, request("집", false));

        assertThat(created.isDefault()).isTrue();
        assertThat(created.phone()).isEqualTo("01012345678");
    }

    @Test
    void 두번째_배송지는_요청대로_기본이_아니다() {
        addressService.create(memberId, request("집", false));

        AddressResponse second = addressService.create(memberId, request("회사", false));

        assertThat(second.isDefault()).isFalse();
    }

    @Test
    void 새_기본_배송지를_지정하면_기존_기본이_해제된다() {
        AddressResponse home = addressService.create(memberId, request("집", true));
        AddressResponse office = addressService.create(memberId, request("회사", true));

        List<AddressResponse> addresses = addressService.list(memberId);

        assertThat(addresses).extracting(AddressResponse::id).containsExactly(office.id(), home.id());
        assertThat(defaults()).containsExactly(true, false);
    }

    @Test
    void 수정으로_기본_배송지를_바꾸면_기존_기본이_해제된다() {
        AddressResponse home = addressService.create(memberId, request("집", true));
        AddressResponse office = addressService.create(memberId, request("회사", false));

        addressService.update(memberId, office.id(), request("회사", true));

        assertThat(addressService.list(memberId)).extracting(AddressResponse::id)
                .containsExactly(office.id(), home.id());
        assertThat(defaults()).containsExactly(true, false);
    }

    @Test
    void 유일한_기본_배송지는_해제되지_않는다() {
        AddressResponse home = addressService.create(memberId, request("집", true));

        AddressResponse updated = addressService.update(memberId, home.id(), request("집", false));

        assertThat(updated.isDefault()).isTrue();
    }

    @Test
    void 다른_배송지가_있어도_기본_배송지를_PUT으로_해제할_수_없다() {
        AddressResponse home = addressService.create(memberId, request("집", true));
        addressService.create(memberId, request("회사", false));

        addressService.update(memberId, home.id(), request("집", false));

        assertThat(defaults()).containsExactly(true, false);
    }

    @Test
    void 기본_배송지를_삭제하면_가장_오래된_배송지가_기본이_된다() {
        AddressResponse home = addressService.create(memberId, request("집", true));
        AddressResponse office = addressService.create(memberId, request("회사", false));
        AddressResponse parents = addressService.create(memberId, request("부모님", false));

        addressService.delete(memberId, home.id());

        List<AddressResponse> addresses = addressService.list(memberId);
        assertThat(addresses).extracting(AddressResponse::id).containsExactly(office.id(), parents.id());
        assertThat(defaults()).containsExactly(true, false);
    }

    @Test
    void 기본이_아닌_배송지를_삭제해도_기본은_그대로다() {
        AddressResponse home = addressService.create(memberId, request("집", true));
        AddressResponse office = addressService.create(memberId, request("회사", false));

        addressService.delete(memberId, office.id());

        assertThat(addressService.list(memberId)).extracting(AddressResponse::id).containsExactly(home.id());
        assertThat(defaults()).containsExactly(true);
    }

    @Test
    void 마지막_배송지를_삭제하면_목록이_비어_있다() {
        AddressResponse home = addressService.create(memberId, request("집", true));

        addressService.delete(memberId, home.id());

        assertThat(addressService.list(memberId)).isEmpty();
    }

    @Test
    void 열한번째_배송지는_ADDRESS_LIMIT_EXCEEDED() {
        for (int i = 1; i <= 10; i++) {
            addressService.create(memberId, request("배송지" + i, false));
        }

        assertThatThrownBy(() -> addressService.create(memberId, request("배송지11", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LIMIT_EXCEEDED));
        assertThat(addressService.list(memberId)).hasSize(10);
    }

    @Test
    void 다른_회원의_배송지_id는_ADDRESS_NOT_FOUND() {
        Long otherId = memberService.signup("other@mall.com", "password1", "김철수", "01011112222");
        AddressResponse home = addressService.create(memberId, request("집", true));

        assertThatThrownBy(() -> addressService.update(otherId, home.id(), request("남의집", true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
        assertThatThrownBy(() -> addressService.delete(otherId, home.id()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
        assertThat(addressService.list(memberId)).hasSize(1);
    }
}
