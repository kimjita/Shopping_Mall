package com.sprint.mission.banner.service;

import com.sprint.mission.banner.domain.Banner;
import com.sprint.mission.banner.dto.BannerRequest;
import com.sprint.mission.banner.dto.BannerResponse;
import com.sprint.mission.banner.repository.BannerRepository;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배너 관리(관리자)와 노출 중인 배너 조회(공개)다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {

    // PostgreSQL timestamptz가 받는 범위 안에서 운영에 의미 있는 연도만 허용한다.
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 9999;

    private final BannerRepository bannerRepository;
    private final Clock clock;

    @Transactional
    public BannerResponse create(BannerRequest request) {
        String title = validate(request);
        Banner banner = Banner.create(title, request.imageUrl(), request.linkUrl(), request.startsAt(),
                request.endsAt(), request.sortOrder());
        return BannerResponse.from(bannerRepository.save(banner));
    }

    /** 전체 교체다. 없는 배너면 {@code BANNER_NOT_FOUND}다. */
    @Transactional
    public BannerResponse update(Long id, BannerRequest request) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
        String title = validate(request);
        banner.update(title, request.imageUrl(), request.linkUrl(), request.startsAt(), request.endsAt(),
                request.sortOrder());
        // 응답에 쓰이는 값은 모두 요청으로 바뀐 필드라 flush 없이 돌려줘도 같다.
        return BannerResponse.from(banner);
    }

    @Transactional
    public void delete(Long id) {
        bannerRepository.delete(bannerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND)));
    }

    public List<BannerResponse> listAll() {
        return bannerRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(BannerResponse::from).toList();
    }

    /** {@code startsAt <= now < endsAt}인 배너를 정렬 순서대로 읽는다. */
    public List<BannerResponse> listActive() {
        Instant now = clock.instant();
        return bannerRepository.findByStartsAtLessThanEqualAndEndsAtGreaterThanOrderBySortOrderAscIdAsc(now, now)
                .stream().map(BannerResponse::from).toList();
    }

    /** 앞뒤 공백을 지운 제목을 돌려준다. 규칙에 맞지 않으면 DB에 닿기 전에 {@code INVALID_INPUT}이다. */
    private static String validate(BannerRequest request) {
        String title = request.title().strip();
        if (title.isEmpty() || title.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "배너 제목은 앞뒤 공백을 지운 값이 1~50자여야 합니다.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "노출 종료 시각은 시작 시각보다 늦어야 합니다.");
        }
        if (!isSupportedYear(request.startsAt()) || !isSupportedYear(request.endsAt())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "노출 시각의 연도는 " + MIN_YEAR + "~" + MAX_YEAR + "년이어야 합니다.");
        }
        return title;
    }

    private static boolean isSupportedYear(Instant instant) {
        int year = instant.atOffset(ZoneOffset.UTC).getYear();
        return year >= MIN_YEAR && year <= MAX_YEAR;
    }
}
