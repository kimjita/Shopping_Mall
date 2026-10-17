package com.sprint.mission.member.controller;

import com.sprint.mission.global.security.CurrentMemberId;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.dto.AddressResponse;
import com.sprint.mission.member.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public List<AddressResponse> list(@CurrentMemberId Long memberId) {
        return addressService.list(memberId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@CurrentMemberId Long memberId, @Valid @RequestBody AddressRequest request) {
        return addressService.create(memberId, request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(@CurrentMemberId Long memberId, @PathVariable Long addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(memberId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@CurrentMemberId Long memberId, @PathVariable Long addressId) {
        addressService.delete(memberId, addressId);
        return ResponseEntity.noContent().build();
    }
}
