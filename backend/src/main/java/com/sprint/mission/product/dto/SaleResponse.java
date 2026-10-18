package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.Sale;
import java.time.Instant;

/** 관리자에게 보이는 세일 정보다. 기간이 아닌 세일도 보이며 {@code active}로 지금 진행 중인지 알린다. */
public record SaleResponse(int rate, Instant startsAt, Instant endsAt, boolean active) {

    /** 세일이 없으면 null이다. */
    public static SaleResponse from(Sale sale, Instant now) {
        if (sale == null) {
            return null;
        }
        return new SaleResponse(sale.getRate(), sale.getStartsAt(), sale.getEndsAt(), sale.isActive(now));
    }
}
