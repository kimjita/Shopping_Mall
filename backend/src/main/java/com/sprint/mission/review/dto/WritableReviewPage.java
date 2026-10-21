package com.sprint.mission.review.dto;

import java.util.List;

/** 커서 방식의 작성 가능한 리뷰 한 페이지다. 마지막 페이지면 nextCursor가 null이다. */
public record WritableReviewPage(List<WritableReviewItem> items, String nextCursor) {
}
