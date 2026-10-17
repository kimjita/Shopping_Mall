package com.sprint.mission.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void 도메인_코드가_아닌_공통_코드만_고른다() {
        assertThat(ErrorCode.from(HttpStatus.CONFLICT)).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 상태_401은_UNAUTHORIZED() {
        assertThat(ErrorCode.from(HttpStatus.UNAUTHORIZED)).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
