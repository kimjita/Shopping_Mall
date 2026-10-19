package com.sprint.mission.order.domain;

import com.sprint.mission.member.domain.Address;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 주문 시점에 주소록에서 복사한 배송지다. 이후 주소가 바뀌어도 주문의 배송지는 그대로다. */
@Embeddable
public record ShippingInfo(
        @Column(nullable = false, length = 30) String recipient,
        @Column(nullable = false, length = 11) String phone,
        @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 5) String zipCode,
        @Column(nullable = false, length = 200) String address1,
        @Column(length = 200) String address2) {

    public static ShippingInfo from(Address address) {
        return new ShippingInfo(address.getRecipient(), address.getPhone(), address.getZipCode(),
                address.getAddress1(), address.getAddress2());
    }
}
