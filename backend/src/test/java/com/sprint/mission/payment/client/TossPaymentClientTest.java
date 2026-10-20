package com.sprint.mission.payment.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TossPaymentClientTest {

    private static final String SECRET = "test_sk_dummy";

    private WireMockServer wireMock;
    private TossPaymentClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        client = new TossPaymentClient(new TossProperties(
                SECRET, URI.create(wireMock.baseUrl()), Duration.ofSeconds(1), Duration.ofMillis(500)));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private static String payment(String status) {
        return """
                {"paymentKey":"pk_1","orderId":"ORD-1","status":"%s","totalAmount":15000,
                 "method":"카드","approvedAt":"2026-10-20T10:00:00+09:00"}
                """.formatted(status);
    }

    private static String error(String code) {
        return """
                {"code":"%s","message":"토스 오류 메시지"}
                """.formatted(code);
    }

    @Test
    void 승인은_Basic_인증과_본문을_보내고_결과를_읽는다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(payment("DONE"))));

        PaymentResult result = client.confirm("pk_1", "ORD-1", 15000);

        assertThat(result).isEqualTo(new PaymentResult(
                "pk_1", "ORD-1", "DONE", 15000, "카드", Instant.parse("2026-10-20T01:00:00Z")));
        assertThat(result.isDone()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/payments/confirm"))
                .withHeader("Authorization", equalTo("Basic dGVzdF9za19kdW1teTo="))
                .withRequestBody(equalToJson("{\"paymentKey\":\"pk_1\",\"orderId\":\"ORD-1\",\"amount\":15000}")));
    }

    @Test
    void 승인_응답에_approvedAt이_없으면_null이다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"paymentKey\":\"pk_1\",\"orderId\":\"ORD-1\",\"status\":\"WAITING_FOR_DEPOSIT\","
                        + "\"totalAmount\":15000,\"method\":\"가상계좌\",\"approvedAt\":null}")));

        PaymentResult result = client.confirm("pk_1", "ORD-1", 15000);

        assertThat(result.approvedAt()).isNull();
        assertThat(result.isDone()).isFalse();
    }

    @Test
    void 승인_거절은_토스_코드를_담은_PaymentGatewayException이다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm")).willReturn(aResponse()
                .withStatus(400).withHeader("Content-Type", "application/json")
                .withBody(error("REJECT_CARD_PAYMENT"))));

        assertThatThrownBy(() -> client.confirm("pk_1", "ORD-1", 15000))
                .isInstanceOfSatisfying(PaymentGatewayException.class,
                        e -> assertThat(e.getCode()).isEqualTo("REJECT_CARD_PAYMENT"));
    }

    @Test
    void 응답이_읽기_타임아웃보다_늦으면_PaymentTimeoutException이다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(payment("DONE")).withFixedDelay(2000)));

        assertThatThrownBy(() -> client.confirm("pk_1", "ORD-1", 15000))
                .isInstanceOf(PaymentTimeoutException.class)
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void 주문번호_조회는_결과를_읽는다() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/orders/ORD-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(payment("DONE"))));

        assertThat(client.findByOrderId("ORD-1")).get().extracting(PaymentResult::paymentKey).isEqualTo("pk_1");
    }

    @Test
    void 주문번호_조회는_없으면_empty다() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/orders/ORD-1")).willReturn(aResponse()
                .withStatus(404).withHeader("Content-Type", "application/json")
                .withBody(error("NOT_FOUND_PAYMENT"))));

        assertThat(client.findByOrderId("ORD-1")).isEmpty();
    }

    @Test
    void 취소는_멱등_키를_보내고_이미_취소됐으면_성공이다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/pk_1/cancel")).willReturn(aResponse()
                .withStatus(400).withHeader("Content-Type", "application/json")
                .withBody(error("ALREADY_CANCELED_PAYMENT"))));

        assertThatCode(() -> client.cancel("pk_1", "고객 요청")).doesNotThrowAnyException();
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/payments/pk_1/cancel"))
                .withHeader("Idempotency-Key", equalTo("cancel-pk_1"))
                .withHeader("Authorization", equalTo("Basic dGVzdF9za19kdW1teTo="))
                .withRequestBody(equalToJson("{\"cancelReason\":\"고객 요청\"}")));
    }

    @Test
    void 취소_거절은_PaymentGatewayException이다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/pk_1/cancel")).willReturn(aResponse()
                .withStatus(400).withHeader("Content-Type", "application/json")
                .withBody(error("NOT_CANCELABLE_PAYMENT"))));

        assertThatThrownBy(() -> client.cancel("pk_1", "고객 요청"))
                .isInstanceOfSatisfying(PaymentGatewayException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_CANCELABLE_PAYMENT"));
    }

    @Test
    void 오류_본문이_없는_5xx는_UNKNOWN_코드의_PaymentGatewayException이다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm")).willReturn(aResponse()
                .withStatus(500).withBody("<html>boom</html>")));

        assertThatThrownBy(() -> client.confirm("pk_1", "ORD-1", 15000))
                .isInstanceOfSatisfying(PaymentGatewayException.class,
                        e -> assertThat(e.getCode()).isEqualTo("UNKNOWN"));
    }

    @Test
    void 토스_5xx_본문의_코드를_담는다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm")).willReturn(aResponse()
                .withStatus(500).withHeader("Content-Type", "application/json")
                .withBody(error("FAILED_INTERNAL_SYSTEM_PROCESSING"))));

        assertThatThrownBy(() -> client.confirm("pk_1", "ORD-1", 15000))
                .isInstanceOfSatisfying(PaymentGatewayException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FAILED_INTERNAL_SYSTEM_PROCESSING"));
    }

    @Test
    void 비밀_키를_예외_메시지에_넣지_않는다() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm")).willReturn(aResponse()
                .withStatus(400).withHeader("Content-Type", "application/json")
                .withBody(error("REJECT_CARD_PAYMENT"))));
        wireMock.stubFor(post(urlEqualTo("/v1/payments/pk_1/cancel")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.confirm("pk_1", "ORD-1", 15000))
                .hasMessageNotContaining(SECRET).hasMessageNotContaining("Basic");
        assertThatThrownBy(() -> client.cancel("pk_1", "고객 요청"))
                .hasMessageNotContaining(SECRET).hasMessageNotContaining("Basic");
    }

    @Test
    void 비밀_키가_비어_있으면_호출할_때_IllegalStateException이다() {
        TossPaymentClient noKey = new TossPaymentClient(new TossProperties(
                "", URI.create(wireMock.baseUrl()), Duration.ofSeconds(1), Duration.ofMillis(500)));

        assertThatThrownBy(() -> noKey.confirm("pk_1", "ORD-1", 15000)).isInstanceOf(IllegalStateException.class);
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }
}
