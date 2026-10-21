package com.sprint.mission.banner.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class AdminBannerApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    private static final String STARTS = "2026-11-01T00:00:00Z";
    private static final String ENDS = "2026-11-30T00:00:00Z";

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private static String body(String title, String imageUrl, String linkUrl, String startsAt, String endsAt,
            int sortOrder) {
        return """
                {"title":"%s","imageUrl":"%s","linkUrl":"%s","startsAt":"%s","endsAt":"%s","sortOrder":%d}"""
                .formatted(title, imageUrl, linkUrl, startsAt, endsAt, sortOrder);
    }

    private static String valid() {
        return body("가을 세일", "https://img.example.com/banner.jpg", "/events/autumn", STARTS, ENDS, 1);
    }

    private ResultActions register(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/banners")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long createValid() throws Exception {
        String location = register(admin(), valid()).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private int bannerCount() {
        return jdbcTemplate.queryForObject("select count(*) from banner", Integer.class);
    }

    private static FieldDescriptor[] requestDescriptors() {
        return new FieldDescriptor[] {
                fieldWithPath("title").type(JsonFieldType.STRING).description("제목 (앞뒤 공백을 지운 값 1~50자)"),
                fieldWithPath("imageUrl").type(JsonFieldType.STRING)
                        .description("이미지 URL (500자 이하, `http(s)://`로 시작한다)"),
                fieldWithPath("linkUrl").type(JsonFieldType.STRING)
                        .description("클릭하면 이동할 링크 (500자 이하, `http(s)://` 또는 `/`로 시작한다)"),
                fieldWithPath("startsAt").type(JsonFieldType.STRING)
                        .description("노출 시작 시각 (ISO-8601 UTC, 이 시각부터 보인다. 연도 2000~9999)"),
                fieldWithPath("endsAt").type(JsonFieldType.STRING)
                        .description("노출 종료 시각 (ISO-8601 UTC, 이 시각부터는 보이지 않는다. 시작보다 늦어야 한다)"),
                fieldWithPath("sortOrder").type(JsonFieldType.NUMBER)
                        .description("정렬 순서 (0~1000, 작을수록 먼저 보인다)")};
    }

    private static FieldDescriptor[] responseDescriptors(String prefix) {
        return new FieldDescriptor[] {
                fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("배너 ID"),
                fieldWithPath(prefix + "title").type(JsonFieldType.STRING).description("제목"),
                fieldWithPath(prefix + "imageUrl").type(JsonFieldType.STRING).description("이미지 URL"),
                fieldWithPath(prefix + "linkUrl").type(JsonFieldType.STRING).description("링크 URL"),
                fieldWithPath(prefix + "startsAt").type(JsonFieldType.STRING).description("노출 시작 시각"),
                fieldWithPath(prefix + "endsAt").type(JsonFieldType.STRING).description("노출 종료 시각"),
                fieldWithPath(prefix + "sortOrder").type(JsonFieldType.NUMBER).description("정렬 순서")};
    }

    @Test
    void 배너를_등록_수정_삭제_목록_조회한다() throws Exception {
        String location = register(admin(), valid())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/admin/banners/")))
                .andExpect(jsonPath("$.title").value("가을 세일"))
                .andExpect(jsonPath("$.startsAt").value(STARTS))
                .andDo(document("admin-banner/create",
                        requestHeaders(AUTHORIZATION),
                        requestFields(requestDescriptors()),
                        responseHeaders(headerWithName(HttpHeaders.LOCATION).description("등록된 배너의 관리자 URL")),
                        responseFields(responseDescriptors(""))))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(put("/api/admin/banners/{bannerId}", id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  겨울 세일  ", "https://img.example.com/winter.jpg", "https://example.com/winter",
                                STARTS, ENDS, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("겨울 세일"))
                .andExpect(jsonPath("$.linkUrl").value("https://example.com/winter"))
                .andExpect(jsonPath("$.sortOrder").value(5))
                .andDo(document("admin-banner/update",
                        pathParameters(parameterWithName("bannerId").description("배너 ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(requestDescriptors()),
                        responseFields(responseDescriptors(""))));

        createValid();
        mockMvc.perform(get("/api/admin/banners").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sortOrder").value(1))
                .andExpect(jsonPath("$[1].id").value(id))
                .andDo(document("admin-banner/list",
                        requestHeaders(AUTHORIZATION),
                        responseFields(responseDescriptors("[]."))));

        mockMvc.perform(delete("/api/admin/banners/{bannerId}", id).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent())
                .andDo(document("admin-banner/delete",
                        pathParameters(parameterWithName("bannerId").description("배너 ID")),
                        requestHeaders(AUTHORIZATION)));
        mockMvc.perform(get("/api/admin/banners").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 시작이_종료보다_늦거나_같으면_400() throws Exception {
        register(admin(), body("배너", "https://img.example.com/a.jpg", "/a", ENDS, STARTS, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        register(admin(), body("배너", "https://img.example.com/a.jpg", "/a", STARTS, STARTS, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(bannerCount()).isZero();
    }

    @Test
    void 아주_먼_연도는_500이_아니라_400() throws Exception {
        register(admin(), body("배너", "https://img.example.com/a.jpg", "/a", STARTS, "+300000-01-01T00:00:00Z", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        register(admin(), body("배너", "https://img.example.com/a.jpg", "/a", "1999-12-31T23:59:59Z", ENDS, 0))
                .andExpect(status().isBadRequest());
        long id = createValid();
        mockMvc.perform(put("/api/admin/banners/{bannerId}", id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("배너", "https://img.example.com/a.jpg", "/a", STARTS,
                                "+300000-01-01T00:00:00Z", 0)))
                .andExpect(status().isBadRequest());
        // 경계: 9999년 안이면 통과한다.
        register(admin(), body("배너", "https://img.example.com/a.jpg", "/a", "2000-01-01T00:00:00Z",
                "9999-12-31T23:59:59Z", 0)).andExpect(status().isCreated());
    }

    @Test
    void 잘못된_URL과_제목_정렬순서는_400() throws Exception {
        String img = "https://img.example.com/a.jpg";
        register(admin(), body("배너", "ftp://img.example.com/a.jpg", "/a", STARTS, ENDS, 0)).andExpect(status().isBadRequest());
        register(admin(), body("배너", img, "javascript:alert(1)", STARTS, ENDS, 0)).andExpect(status().isBadRequest());
        register(admin(), body("   ", img, "/a", STARTS, ENDS, 0)).andExpect(status().isBadRequest());
        register(admin(), body("가".repeat(51), img, "/a", STARTS, ENDS, 0)).andExpect(status().isBadRequest());
        register(admin(), body("배너", img, "/a", STARTS, ENDS, 1001)).andExpect(status().isBadRequest());
        register(admin(), body("배너", img, "/a", STARTS, ENDS, -1)).andExpect(status().isBadRequest());
        assertThat(bannerCount()).isZero();
    }

    @Test
    void 프로토콜_상대_링크와_역슬래시_링크는_400이고_서비스_경로와_절대_주소는_통과한다() throws Exception {
        String img = "https://img.example.com/a.jpg";
        register(admin(), body("배너", img, "//evil.com/x", STARTS, ENDS, 0)).andExpect(status().isBadRequest());
        register(admin(), body("배너", img, "/\\\\evil.com", STARTS, ENDS, 0)).andExpect(status().isBadRequest());
        assertThat(bannerCount()).isZero();
        register(admin(), body("배너", img, "/events/1", STARTS, ENDS, 0)).andExpect(status().isCreated());
        register(admin(), body("배너", img, "https://example.com", STARTS, ENDS, 0)).andExpect(status().isCreated());
    }

    @Test
    void 없는_배너는_404() throws Exception {
        mockMvc.perform(put("/api/admin/banners/{bannerId}", 999)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
        mockMvc.perform(delete("/api/admin/banners/{bannerId}", 999).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
    }

    @Test
    void 일반_회원은_403_토큰이_없으면_401() throws Exception {
        register(bearer(2L, Role.USER), valid()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/banners")).andExpect(status().isUnauthorized());
    }
}
