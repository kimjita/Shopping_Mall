package com.sprint.mission.member.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Address;
import com.sprint.mission.member.domain.PhonePolicy;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.dto.AddressResponse;
import com.sprint.mission.member.repository.AddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송지가 1개 이상인 회원은 항상 기본 배송지가 정확히 1개예요.
 * 회원당 최대 10개라서 목록을 통째로 읽어 기본 여부를 맞춰요.
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    static final int MAX_ADDRESSES = 10;

    private final MemberService memberService;
    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long memberId) {
        memberService.getActive(memberId);
        return addressRepository.findAllByMemberId(memberId).stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse create(Long memberId, AddressRequest request) {
        memberService.getActive(memberId);
        List<Address> existing = addressRepository.findAllByMemberId(memberId);
        if (existing.size() >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        boolean isDefault = existing.isEmpty() || request.isDefault();
        if (isDefault) {
            existing.forEach(Address::unmarkDefault);
        }
        Address address = Address.create(memberId, request.recipient(), PhonePolicy.normalize(request.phone()),
                request.zipCode(), request.address1(), request.address2(), isDefault);
        return AddressResponse.from(addressRepository.save(address));
    }

    /** 기본 배송지를 없애는 요청(isDefault=false)은 무시해요. 다른 배송지를 기본으로 지정해야 바뀌어요. */
    @Transactional
    public AddressResponse update(Long memberId, Long addressId, AddressRequest request) {
        memberService.getActive(memberId);
        Address address = findOwned(addressId, memberId);
        address.update(request.recipient(), PhonePolicy.normalize(request.phone()),
                request.zipCode(), request.address1(), request.address2());
        if (request.isDefault()) {
            addressRepository.findAllByMemberId(memberId).forEach(Address::unmarkDefault);
            address.markDefault();
        }
        return AddressResponse.from(address);
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        memberService.getActive(memberId);
        Address address = findOwned(addressId, memberId);
        addressRepository.delete(address);
        if (address.isDefault()) {
            addressRepository.findAllByMemberId(memberId).stream()
                    .filter(other -> !other.getId().equals(addressId))
                    .findFirst()
                    .ifPresent(Address::markDefault);
        }
    }

    private Address findOwned(Long addressId, Long memberId) {
        return addressRepository.findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
    }
}
