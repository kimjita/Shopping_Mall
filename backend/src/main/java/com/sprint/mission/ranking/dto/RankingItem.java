package com.sprint.mission.ranking.dto;

import com.sprint.mission.product.dto.ProductSummary;

/** 랭킹 한 줄이다. rank는 1부터 시작한다. */
public record RankingItem(int rank, ProductSummary product) {
}
