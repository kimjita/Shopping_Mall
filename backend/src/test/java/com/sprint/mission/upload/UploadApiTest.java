package com.sprint.mission.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.support.ApiTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;

class UploadApiTest extends ApiTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${app.storage.public-base-url}")
    String publicBaseUrl;

    @Value("${app.storage.bucket}")
    String bucket;

    @Autowired
    S3Client s3Client;

    private ResultActions presign(String token, String purpose, String contentType, long size) throws Exception {
        return mockMvc.perform(post("/api/uploads")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"purpose":"%s","contentType":"%s","size":%d}""".formatted(purpose, contentType, size)));
    }

    private JsonNode presignOk(String purpose, String contentType, long size) throws Exception {
        String body = presign(bearer(1L, Role.ADMIN), purpose, contentType, size)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static int put(String url, String contentType, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static byte[] bytes(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 7);
        return bytes;
    }

    @Test
    void 관리자는_상품_이미지_업로드_URL을_받고_그대로_올릴_수_있다() throws Exception {
        String body = presign(bearer(1L, Role.ADMIN), "PRODUCT", "image/png", 68)
                .andExpect(status().isOk())
                .andDo(document("upload/presign",
                        requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION)
                                .description("access token (`Bearer <token>`)")),
                        requestFields(
                                fieldWithPath("purpose").type(JsonFieldType.STRING)
                                        .description("용도 (`PRODUCT`·`BANNER`는 관리자만, `REVIEW`는 로그인 회원 누구나)"),
                                fieldWithPath("contentType").type(JsonFieldType.STRING)
                                        .description("이미지 형식 (`image/jpeg`·`image/png`·`image/webp` 중 하나와 정확히 같아야 한다)"),
                                fieldWithPath("size").type(JsonFieldType.NUMBER)
                                        .description("파일 크기 바이트 (1~5,242,880)")),
                        responseFields(
                                fieldWithPath("uploadUrl").type(JsonFieldType.STRING)
                                        .description("파일을 `PUT`으로 올릴 서명 URL (요청한 형식·크기로만 올릴 수 있다)"),
                                fieldWithPath("imageUrl").type(JsonFieldType.STRING)
                                        .description("올린 뒤 이미지를 읽을 공개 URL"),
                                fieldWithPath("expiresAt").type(JsonFieldType.STRING)
                                        .description("`uploadUrl` 만료 시각 (ISO-8601 UTC, 발급 5분 뒤)"))))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        String imageUrl = response.get("imageUrl").asText();
        assertThat(imageUrl).startsWith(publicBaseUrl + "/product/").endsWith(".png")
                .matches(".*/product/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.png");
        assertThat(Instant.parse(response.get("expiresAt").asText()))
                .isCloseTo(Instant.now().plus(5, ChronoUnit.MINUTES), within(30, ChronoUnit.SECONDS));

        byte[] png = bytes(68);
        assertThat(put(response.get("uploadUrl").asText(), "image/png", png)).isEqualTo(200);

        HttpResponse<byte[]> image = HTTP.send(HttpRequest.newBuilder(URI.create(imageUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(png);
        String key = imageUrl.substring(publicBaseUrl.length() + 1);
        assertThat(s3Client.headObject(b -> b.bucket(bucket).key(key)).contentType()).isEqualTo("image/png");
    }

    @Test
    void 회원은_리뷰_사진_URL을_받는다() throws Exception {
        presign(bearer(2L, Role.USER), "REVIEW", "image/jpeg", 1024)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.matchesPattern(
                        ".*/review/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.jpg")));
        presign(bearer(2L, Role.USER), "REVIEW", "image/webp", 1024)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.endsWith(".webp")));
    }

    @Test
    void 회원이_상품_이미지_URL을_요청하면_403() throws Exception {
        for (String purpose : new String[] {"PRODUCT", "BANNER"}) {
            presign(bearer(2L, Role.USER), purpose, "image/png", 68)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void 비로그인이면_401() throws Exception {
        mockMvc.perform(post("/api/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"REVIEW","contentType":"image/png","size":68}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 허용하지_않는_형식이면_400() throws Exception {
        for (String contentType : new String[] {"image/svg+xml", "text/html", "image/jpeg; charset=utf-8",
                "IMAGE/PNG"}) {
            presign(bearer(1L, Role.ADMIN), "PRODUCT", contentType, 68)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 크기가_0이거나_5MB를_넘으면_400() throws Exception {
        for (long size : new long[] {0, 5_242_881}) {
            presign(bearer(1L, Role.ADMIN), "PRODUCT", "image/png", size)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        presign(bearer(1L, Role.ADMIN), "PRODUCT", "image/png", 5_242_880).andExpect(status().isOk());
    }

    @Test
    void 서명과_다른_형식이나_크기로_올리면_S3가_거절한다() throws Exception {
        JsonNode png = presignOk("PRODUCT", "image/png", 68);
        assertThat(put(png.get("uploadUrl").asText(), "image/jpeg", bytes(68))).isGreaterThanOrEqualTo(300);

        JsonNode sized = presignOk("PRODUCT", "image/png", 100);
        assertThat(put(sized.get("uploadUrl").asText(), "image/png", bytes(99))).isGreaterThanOrEqualTo(300);
    }
}
