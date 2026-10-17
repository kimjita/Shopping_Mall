package com.sprint.mission.member.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 회원의 배송지예요. 휴대폰 번호는 숫자만 저장해요. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 30)
    private String recipient;

    @Column(nullable = false, length = 11)
    private String phone;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 5)
    private String zipCode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(nullable = false)
    private boolean isDefault;

    public static Address create(Long memberId, String recipient, String phone, String zipCode,
                                 String address1, String address2, boolean isDefault) {
        Address address = new Address();
        address.memberId = memberId;
        address.isDefault = isDefault;
        address.update(recipient, phone, zipCode, address1, address2);
        return address;
    }

    public void update(String recipient, String phone, String zipCode, String address1, String address2) {
        this.recipient = recipient;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }
}
