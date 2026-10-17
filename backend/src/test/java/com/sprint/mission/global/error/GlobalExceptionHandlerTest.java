package com.sprint.mission.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.support.ApiTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

class GlobalExceptionHandlerTest extends ApiTest {

    @Test
    void 비즈니스_예외는_ErrorCode의_상태와_메시지로_응답한다() throws Exception {
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.errors", empty()))
                .andDo(document("common/error-response", responseFields(
                        fieldWithPath("code").type(JsonFieldType.STRING).description("에러 코드 (ErrorCode enum 이름)"),
                        fieldWithPath("message").type(JsonFieldType.STRING).description("사용자에게 보여줄 에러 메시지"),
                        fieldWithPath("errors").type(JsonFieldType.ARRAY).description("필드 검증 오류 목록 (없으면 빈 배열)"),
                        fieldWithPath("errors[].field").type(JsonFieldType.STRING).description("오류가 난 필드 이름").optional(),
                        fieldWithPath("errors[].reason").type(JsonFieldType.STRING).description("오류 사유").optional())));
    }

    @Test
    void 검증_실패는_필드별_오류를_담는다() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "quantity")));
    }

    @Test
    void 깨진_JSON은_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 한글_메시지가_깨지지_않는다() throws Exception {
        String body = mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("입력값이 올바르지 않습니다.");
    }

    @Test
    void 없는_URL은_공통_포맷_404로_응답한다() throws Exception {
        mockMvc.perform(get("/test/errors/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 지원하지_않는_메서드는_405로_응답한다() throws Exception {
        mockMvc.perform(delete("/test/errors/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 예상치_못한_예외는_내부_메시지를_숨긴다() throws Exception {
        String body = mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("secret");
    }

    @Test
    void 지원하지_않는_Content_Type은_415로_응답한다() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void 요청_파라미터_검증_실패는_400과_필드_오류로_응답한다() throws Exception {
        mockMvc.perform(get("/test/errors/param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }
}
