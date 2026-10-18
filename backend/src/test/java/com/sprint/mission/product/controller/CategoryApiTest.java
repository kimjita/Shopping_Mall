package com.sprint.mission.product.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;

class CategoryApiTest extends ApiTest {

    @Test
    void 카테고리_트리를_비회원도_조회한다() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains(
                        "스킨케어", "마스크팩", "클렌징", "선케어", "메이크업", "바디케어", "헤어케어")))
                .andExpect(jsonPath("$[4].children[0].name").value("립"))
                .andExpect(jsonPath("$[4].children[0].children[0].name").value("립스틱"))
                .andExpect(jsonPath("$[0].children[0].children.length()").value(0))
                .andDo(document("category/tree",
                        responseFields(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                subsectionWithPath("[].children").type(JsonFieldType.ARRAY)
                                        .description("하위 카테고리 목록. 같은 구조가 최대 3단계까지 이어지고, 가장 아래 단계는 빈 배열이다."))));
    }

    @Test
    void 테스트가_끝나도_시드_카테고리는_남는다() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));
    }
}
