package com.sprint.mission.payment;

import com.sprint.mission.payment.client.PaymentGateway;
import com.sprint.mission.payment.client.PaymentResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 토스 대신 쓰는 결제사다. 호출을 기록하고, 테스트가 응답·예외·지연을 정한다. 기본 승인은 요청 그대로의 DONE 카드 결제이고,
 * 기본 조회는 결제 없음이다. 빈 하나를 여러 테스트가 같이 쓰므로 테스트가 끝나면 {@link #reset()}을 부른다.
 */
public class FakePaymentGateway implements PaymentGateway {

    /** 테스트 클래스에 {@code @Import(FakePaymentGateway.Config.class)}로 붙인다. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }
    }

    public record ConfirmCall(String paymentKey, String orderId, long amount) {
    }

    public record CancelCall(String paymentKey, String cancelReason) {
    }

    private final List<ConfirmCall> confirms = new CopyOnWriteArrayList<>();
    private final List<CancelCall> cancels = new CopyOnWriteArrayList<>();
    private final List<String> finds = new CopyOnWriteArrayList<>();

    private volatile PaymentResult confirmResult;
    private volatile RuntimeException confirmError;
    private volatile Duration confirmDelay = Duration.ZERO;
    private volatile Optional<PaymentResult> findResult = Optional.empty();
    private volatile RuntimeException findError;
    private volatile RuntimeException cancelError;
    private volatile Duration cancelDelay = Duration.ZERO;

    @Override
    public PaymentResult confirm(String paymentKey, String orderId, long amount) {
        confirms.add(new ConfirmCall(paymentKey, orderId, amount));
        sleep(confirmDelay);
        if (confirmError != null) {
            throw confirmError;
        }
        return confirmResult != null ? confirmResult : done(paymentKey, orderId, amount);
    }

    @Override
    public Optional<PaymentResult> findByOrderId(String orderId) {
        finds.add(orderId);
        if (findError != null) {
            throw findError;
        }
        return findResult;
    }

    @Override
    public void cancel(String paymentKey, String cancelReason) {
        cancels.add(new CancelCall(paymentKey, cancelReason));
        sleep(cancelDelay);
        if (cancelError != null) {
            throw cancelError;
        }
    }

    private static void sleep(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static PaymentResult done(String paymentKey, String orderId, long amount) {
        return new PaymentResult(paymentKey, orderId, "DONE", amount, "카드", Instant.parse("2026-10-20T01:00:00Z"));
    }

    public void confirmReturns(PaymentResult result) {
        this.confirmResult = result;
    }

    public void confirmThrows(RuntimeException error) {
        this.confirmError = error;
    }

    public void delayConfirm(Duration delay) {
        this.confirmDelay = delay;
    }

    public void findReturns(PaymentResult result) {
        this.findResult = Optional.of(result);
    }

    public void findThrows(RuntimeException error) {
        this.findError = error;
    }

    public void cancelThrows(RuntimeException error) {
        this.cancelError = error;
    }

    public void delayCancel(Duration delay) {
        this.cancelDelay = delay;
    }

    public List<ConfirmCall> confirms() {
        return List.copyOf(confirms);
    }

    public List<CancelCall> cancels() {
        return List.copyOf(cancels);
    }

    public List<String> finds() {
        return List.copyOf(finds);
    }

    public void reset() {
        confirms.clear();
        cancels.clear();
        finds.clear();
        confirmResult = null;
        confirmError = null;
        confirmDelay = Duration.ZERO;
        findResult = Optional.empty();
        findError = null;
        cancelError = null;
        cancelDelay = Duration.ZERO;
    }
}
