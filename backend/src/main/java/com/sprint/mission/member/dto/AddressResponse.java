package com.sprint.mission.member.dto;

import com.sprint.mission.member.domain.Address;

public record AddressResponse(
        Long id,
        String recipient,
        String phone,
        String zipCode,
        String address1,
        String address2,
        boolean isDefault) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getRecipient(), address.getPhone(),
                address.getZipCode(), address.getAddress1(), address.getAddress2(), address.isDefault());
    }
}
