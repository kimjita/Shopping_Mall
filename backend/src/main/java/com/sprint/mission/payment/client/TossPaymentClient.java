package com.sprint.mission.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class TossPaymentClient implements PaymentGateway {

    private static final String ALREADY_CANCELED = "ALREADY_CANCELED_PAYMENT";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String secretKey;

    public TossPaymentClient(TossProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
        this.secretKey = properties.secretKey();
    }

    @Override
    public PaymentResult confirm(String paymentKey, String orderId, long amount) {
        Response response = send(HttpMethod.POST, "/v1/payments/confirm", null,
                Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount));
        return response.okOrThrow();
    }

    @Override
    public Optional<PaymentResult> findByOrderId(String orderId) {
        Response response = send(HttpMethod.GET, "/v1/payments/orders/{orderId}", null, null, orderId);
        if (response.status == 404) {
            return Optional.empty();
        }
        return Optional.of(response.okOrThrow());
    }

    @Override
    public void cancel(String paymentKey, String cancelReason) {
        Response response = send(HttpMethod.POST, "/v1/payments/{paymentKey}/cancel", "cancel-" + paymentKey,
                Map.of("cancelReason", cancelReason), paymentKey);
        if (!ALREADY_CANCELED.equals(response.errorCode())) {
            response.okOrThrow();
        }
    }

    private Response send(HttpMethod method, String uri, String idempotencyKey, Object body, Object... uriVars) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("app.toss.secret-key 설정이 필요하다.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            RestClient.RequestBodySpec spec = restClient.method(method).uri(uri, uriVars)
                    .header("Authorization", "Basic " + basic)
                    .accept(MediaType.APPLICATION_JSON);
            if (idempotencyKey != null) {
                spec.header("Idempotency-Key", idempotencyKey);
            }
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return spec.exchange((req, res) -> new Response(res.getStatusCode().value(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (ResourceAccessException e) {
            // 원인 예외 메시지에는 요청 헤더가 들어 있지 않지만, 호출 쪽으로는 고정 문구만 올린다.
            throw new PaymentTimeoutException("결제사 연결 또는 응답 시간이 초과되었다.", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        String errorCode() {
            if (status < 400) {
                return null;
            }
            JsonNode node = parse(body);
            return node != null && node.hasNonNull("code") ? node.get("code").asText() : "UNKNOWN";
        }

        PaymentResult okOrThrow() {
            if (status >= 400) {
                JsonNode node = parse(body);
                String message = node != null && node.hasNonNull("message") ? node.get("message").asText()
                        : "HTTP " + status;
                throw new PaymentGatewayException(errorCode(), message);
            }
            JsonNode node = parse(body);
            if (node == null) {
                throw new PaymentGatewayException("UNKNOWN", "결제사 응답을 읽지 못했다.");
            }
            return new PaymentResult(node.path("paymentKey").asText(null), node.path("orderId").asText(null),
                    node.path("status").asText(null), node.path("totalAmount").asLong(),
                    node.path("method").asText(null),
                    node.hasNonNull("approvedAt") ? OffsetDateTime.parse(node.get("approvedAt").asText()).toInstant()
                            : null);
        }
    }
}
