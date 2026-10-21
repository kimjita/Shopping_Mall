package com.sprint.mission.banner.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.banner.domain.Banner;
import com.sprint.mission.banner.repository.BannerRepository;
import com.sprint.mission.support.ApiTest;
import com.sprint.mission.support.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.restdocs.payload.JsonFieldType;

class BannerApiTest extends ApiTest {

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    private static final Instant T0 = Instant.parse("2026-11-10T00:00:00Z");

    @Autowired
    MutableClock clock;

    @Autowired
    BannerRepository bannerRepository;

    private Long save(String title, Instant startsAt, Instant endsAt, int sortOrder) {
        return bannerRepository.save(Banner.create(title, "https://img.example.com/" + title + ".jpg",
                "/events/" + title, startsAt, endsAt, sortOrder)).getId();
    }

    @Test
    void 노출_기간_안의_배너만_순서대로_보인다() throws Exception {
        clock.set(T0);
        Long late = save("늦은순서", T0.minusSeconds(60), T0.plusSeconds(60), 5);
        Long tieFirst = save("동률먼저", T0.minusSeconds(60), T0.plusSeconds(60), 1);
        Long tieSecond = save("동률나중", T0, T0.plusSeconds(60), 1); // startsAt 정각이라 보인다
        save("종료정각", T0.minusSeconds(60), T0, 0); // endsAt 정각이라 안 보인다
        save("아직", T0.plusMillis(1), T0.plusSeconds(60), 0);
        save("지남", T0.minusSeconds(120), T0.minusSeconds(60), 0);

        mockMvc.perform(get("/api/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(tieFirst))
                .andExpect(jsonPath("$[1].id").value(tieSecond))
                .andExpect(jsonPath("$[2].id").value(late))
                .andDo(document("banner/list",
                        responseFields(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("배너 ID"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).description("제목"),
                                fieldWithPath("[].imageUrl").type(JsonFieldType.STRING).description("이미지 URL"),
                                fieldWithPath("[].linkUrl").type(JsonFieldType.STRING)
                                        .description("클릭하면 이동할 링크. `/`로 시작하면 서비스 안의 경로다"),
                                fieldWithPath("[].startsAt").type(JsonFieldType.STRING).description("노출 시작 시각"),
                                fieldWithPath("[].endsAt").type(JsonFieldType.STRING).description("노출 종료 시각"),
                                fieldWithPath("[].sortOrder").type(JsonFieldType.NUMBER).description("정렬 순서"))));

        // 시간이 흐르면 종료 정각에 사라지고 시작 정각에 나타난다.
        clock.set(T0.plusMillis(1));
        mockMvc.perform(get("/api/banners")).andExpect(jsonPath("$.length()").value(4));
        clock.set(T0.plusSeconds(60));
        mockMvc.perform(get("/api/banners")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 토큰_없이도_읽는다() throws Exception {
        mockMvc.perform(get("/api/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
