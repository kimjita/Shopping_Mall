package com.sprint.mission.product.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.domain.Brand;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.BrandResponse;
import com.sprint.mission.product.repository.BrandRepository;
import com.sprint.mission.product.repository.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    @Transactional
    public BrandResponse create(BrandRequest request) {
        if (brandRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BRAND);
        }
        try {
            return BrandResponse.from(
                    brandRepository.saveAndFlush(Brand.create(request.name(), request.description())));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_BRAND);
        }
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = get(id);
        if (brandRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_BRAND);
        }
        boolean renamed = !brand.getName().equals(request.name());
        brand.update(request.name(), request.description());
        try {
            brandRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_BRAND);
        }
        if (renamed) {
            // 검색 문자열에 브랜드 이름이 들어 있으므로 그 브랜드 상품 전체를 한 문장으로 다시 만든다.
            productRepository.refreshSearchTextByBrand(id, request.name(), Instant.now(clock));
        }
        return BrandResponse.from(brand);
    }

    /** 이름 오름차순 전체 목록이다. */
    public List<BrandResponse> list() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .map(BrandResponse::from)
                .toList();
    }

    /** 없으면 BRAND_NOT_FOUND다. */
    public Brand get(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND));
    }
}
