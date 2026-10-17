package com.sprint.mission.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.support.TestcontainersConfig;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, ErrorContractIntegrationTest.FailingFilterConfig.class})
class ErrorContractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void 필터에서_발생한_예외도_공통_포맷으로_응답한다() {
        ResponseEntity<String> response = restTemplate.getForEntity("/test/filter-error", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("\"code\":\"INTERNAL_ERROR\"");
        assertThat(response.getBody()).doesNotContain("filter secret");
    }

    @Test
    void HTML만_받는_요청의_없는_URL도_JSON_404로_응답한다() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));

        ResponseEntity<String> response = restTemplate.exchange(
                "/nope", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingFilterConfig {

        @Bean
        FilterRegistrationBean<Filter> failingFilter() {
            Filter filter = (request, response, chain) -> {
                throw new IllegalStateException("filter secret");
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns("/test/filter-error");
            return registration;
        }
    }
}
