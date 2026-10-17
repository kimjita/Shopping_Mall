package com.sprint.mission.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.dto.AddressResponse;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AddressApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    AddressService addressService;

    Long memberId;

    @BeforeEach
    void 회원을_가입시킨다() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private String token() {
        return bearer(memberId, Role.USER);
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(String recipient, String zipCode, boolean isDefault) {
        return """
                {"recipient":"%s","phone":"010-1234-5678","zipCode":"%s","address1":"서울시 강남구 테헤란로 1","address2":"101호","isDefault":%s}"""
                .formatted(recipient, zipCode, isDefault);
    }

    private AddressResponse createAddress(Long ownerId, String recipient, boolean isDefault) {
        return addressService.create(ownerId, new AddressRequest(
                recipient, "01012345678", "06236", "서울시 강남구 테헤란로 1", "101호", isDefault));
    }

    private static FieldDescriptor[] addressFields(String prefix) {
        return new FieldDescriptor[] {
                fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("배송지 ID"),
                fieldWithPath(prefix + "recipient").type(JsonFieldType.STRING).description("받는 사람"),
                fieldWithPath(prefix + "phone").type(JsonFieldType.STRING).description("휴대폰 번호 (숫자만)"),
                fieldWithPath(prefix + "zipCode").type(JsonFieldType.STRING).description("우편번호 (5자리)"),
                fieldWithPath(prefix + "address1").type(JsonFieldType.STRING).description("기본 주소"),
                fieldWithPath(prefix + "address2").type(JsonFieldType.STRING).optional().description("상세 주소. 없으면 `null`"),
                fieldWithPath(prefix + "isDefault").type(JsonFieldType.BOOLEAN).description("기본 배송지 여부")
        };
    }

    private static FieldDescriptor[] requestAddressFields() {
        return new FieldDescriptor[] {
                fieldWithPath("recipient").type(JsonFieldType.STRING).description("받는 사람 (1~30자)"),
                fieldWithPath("phone").type(JsonFieldType.STRING)
                        .description("휴대폰 번호 (하이픈 허용, 저장할 때는 숫자만 남김)"),
                fieldWithPath("zipCode").type(JsonFieldType.STRING).description("우편번호 (숫자 5자리)"),
                fieldWithPath("address1").type(JsonFieldType.STRING).description("기본 주소 (1~200자)"),
                fieldWithPath("address2").type(JsonFieldType.STRING).optional().description("상세 주소 (200자 이하, 생략 가능)"),
                fieldWithPath("isDefault").type(JsonFieldType.BOOLEAN).optional()
                        .description("기본 배송지로 지정할지 여부. 생략하면 `false`")
        };
    }

    @Test
    void 배송지를_등록하고_목록을_조회한다() throws Exception {
        json(post("/api/members/me/addresses"), token(), body("집", "06236", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.recipient").value("집"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.zipCode").value("06236"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andDo(document("address/create",
                        requestHeaders(AUTHORIZATION),
                        requestFields(requestAddressFields()),
                        responseFields(addressFields(""))));
        createAddress(memberId, "회사", false);

        mockMvc.perform(get("/api/members/me/addresses").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].recipient").value("집"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].recipient").value("회사"))
                .andExpect(jsonPath("$[1].isDefault").value(false))
                .andDo(document("address/list",
                        requestHeaders(AUTHORIZATION),
                        responseFields(addressFields("[]."))));
    }

    @Test
    void 배송지를_수정한다() throws Exception {
        createAddress(memberId, "집", true);
        AddressResponse office = createAddress(memberId, "회사", false);

        json(put("/api/members/me/addresses/{addressId}", office.id()), token(), body("새회사", "04524", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(office.id()))
                .andExpect(jsonPath("$.recipient").value("새회사"))
                .andExpect(jsonPath("$.zipCode").value("04524"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andDo(document("address/update",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("addressId").description("수정할 배송지 ID")),
                        requestFields(requestAddressFields()),
                        responseFields(addressFields(""))));

        mockMvc.perform(get("/api/members/me/addresses").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$[0].id").value(office.id()))
                .andExpect(jsonPath("$[1].isDefault").value(false));
    }

    @Test
    void 배송지를_삭제한다() throws Exception {
        AddressResponse home = createAddress(memberId, "집", true);
        AddressResponse office = createAddress(memberId, "회사", false);

        mockMvc.perform(delete("/api/members/me/addresses/{addressId}", home.id())
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andDo(document("address/delete",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("addressId").description("삭제할 배송지 ID"))));

        mockMvc.perform(get("/api/members/me/addresses").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(office.id()))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void 다른_회원의_배송지는_수정할_수_없다() throws Exception {
        AddressResponse home = createAddress(memberId, "집", true);
        Long otherId = memberService.signup("other@mall.com", "password1", "김철수", "01011112222");

        json(put("/api/members/me/addresses/{addressId}", home.id()), bearer(otherId, Role.USER),
                body("남의집", "04524", true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));

        AddressResponse unchanged = addressService.list(memberId).get(0);
        assertThat(unchanged.recipient()).isEqualTo("집");
        assertThat(unchanged.zipCode()).isEqualTo("06236");
        assertThat(addressService.list(otherId)).isEmpty();
    }

    @Test
    void 다른_회원의_배송지는_삭제할_수_없다() throws Exception {
        AddressResponse home = createAddress(memberId, "집", true);
        Long otherId = memberService.signup("other@mall.com", "password1", "김철수", "01011112222");

        mockMvc.perform(delete("/api/members/me/addresses/{addressId}", home.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherId, Role.USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));

        assertThat(addressService.list(memberId)).hasSize(1);
    }

    @Test
    void 우편번호_형식이_틀리면_400() throws Exception {
        String response = json(post("/api/members/me/addresses"), token(), body("집", "1234", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andReturn().getResponse().getContentAsString();

        JsonNode errors = objectMapper.readTree(response).get("errors");
        assertThat(errors).anySatisfy(error -> assertThat(error.get("field").asText()).isEqualTo("zipCode"));
        assertThat(addressService.list(memberId)).isEmpty();
    }

    @Test
    void 필수값이_비어_있으면_400() throws Exception {
        json(post("/api/members/me/addresses"), token(),
                "{\"recipient\":\" \",\"phone\":\"123\",\"zipCode\":null,\"address1\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[?(@.field=='recipient')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='phone')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='zipCode')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='address1')]").exists());
    }

    @Test
    void 열한번째_배송지는_400_ADDRESS_LIMIT_EXCEEDED() throws Exception {
        for (int i = 1; i <= 10; i++) {
            createAddress(memberId, "배송지" + i, false);
        }

        json(post("/api/members/me/addresses"), token(), body("배송지11", "06236", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_EXCEEDED"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(get("/api/members/me/addresses"))
                .andExpect(status().isUnauthorized());
    }
}
