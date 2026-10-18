package com.sprint.mission.product.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class BrandApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    BrandService brandService;

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(String name, String description) {
        return description == null
                ? """
                {"name":"%s"}""".formatted(name)
                : """
                {"name":"%s","description":"%s"}""".formatted(name, description);
    }

    private static FieldDescriptor[] brandFields() {
        return new FieldDescriptor[] {
                fieldWithPath("id").type(JsonFieldType.NUMBER).description("브랜드 ID"),
                fieldWithPath("name").type(JsonFieldType.STRING).description("브랜드 이름"),
                fieldWithPath("description").type(JsonFieldType.STRING).optional()
                        .description("브랜드 설명. 없으면 `null`")
        };
    }

    private static FieldDescriptor[] brandRequestFields() {
        return new FieldDescriptor[] {
                fieldWithPath("name").type(JsonFieldType.STRING)
                        .description("브랜드 이름 (앞뒤 공백을 지운 뒤 1~50자, 중복 불가)"),
                fieldWithPath("description").type(JsonFieldType.STRING).optional()
                        .description("브랜드 설명 (500자 이하, 생략 가능)")
        };
    }

    @Test
    void 관리자가_브랜드를_등록한다() throws Exception {
        json(post("/api/admin/brands"), admin(), body("  라운드랩  ", "독도 토너로 유명한 브랜드"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("라운드랩"))
                .andExpect(jsonPath("$.description").value("독도 토너로 유명한 브랜드"))
                .andDo(document("brand/create",
                        requestHeaders(AUTHORIZATION),
                        requestFields(brandRequestFields()),
                        responseFields(brandFields())));
    }

    @Test
    void 관리자가_브랜드를_수정한다() throws Exception {
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();

        json(put("/api/admin/brands/{brandId}", brandId), admin(), body("아누아", "어성초 라인"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(brandId))
                .andExpect(jsonPath("$.name").value("아누아"))
                .andExpect(jsonPath("$.description").value("어성초 라인"))
                .andDo(document("brand/update",
                        pathParameters(parameterWithName("brandId").description("수정할 브랜드 ID")),
                        requestHeaders(AUTHORIZATION),
                        requestFields(brandRequestFields()),
                        responseFields(brandFields())));
    }

    @Test
    void 비회원도_브랜드_목록을_본다() throws Exception {
        brandService.create(new BrandRequest("토리든", "다이브인 세럼"));
        brandService.create(new BrandRequest("라운드랩", null));
        brandService.create(new BrandRequest("아누아", "어성초 라인"));

        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("라운드랩", "아누아", "토리든")))
                .andDo(document("brand/list",
                        responseFields(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("브랜드 ID"),
                                fieldWithPath("[].name").type(JsonFieldType.STRING).description("브랜드 이름"),
                                fieldWithPath("[].description").type(JsonFieldType.STRING).optional()
                                        .description("브랜드 설명. 없으면 `null`"))));
    }

    @Test
    void 같은_이름은_409() throws Exception {
        brandService.create(new BrandRequest("라운드랩", null));
        Long otherId = brandService.create(new BrandRequest("아누아", null)).id();

        json(post("/api/admin/brands"), admin(), body("라운드랩", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BRAND"));

        json(put("/api/admin/brands/{brandId}", otherId), admin(), body("라운드랩", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BRAND"));
    }

    @Test
    void 자기_이름_그대로_수정하면_200() throws Exception {
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();

        json(put("/api/admin/brands/{brandId}", brandId), admin(), body("라운드랩", "설명만 바꾼다"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("라운드랩"))
                .andExpect(jsonPath("$.description").value("설명만 바꾼다"));
    }

    @Test
    void USER는_브랜드를_등록할_수_없다() throws Exception {
        json(post("/api/admin/brands"), bearer(1L, Role.USER), body("라운드랩", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 없는_브랜드_수정은_404() throws Exception {
        json(put("/api/admin/brands/{brandId}", 9999L), admin(), body("라운드랩", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));
    }

    @Test
    void 이름이_비었거나_너무_길면_400() throws Exception {
        json(post("/api/admin/brands"), admin(), body("   ", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        json(post("/api/admin/brands"), admin(), body("가".repeat(51), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
