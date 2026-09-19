package dev.infrai.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
final class InfraiFailureLedger implements AgentPorts.FailureLedger {
    private static final String ERRORS_CAPTURE_PATH = "/v1/errors/capture";
    private final InfraiSettings settings;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    InfraiFailureLedger(InfraiSettings settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
    }

    @Override
    public void capture(String operationKey, RuntimeException failure, CheckoutWorkflow.Order order, long consumedTokens) {
        Map<String, Object> exception = Map.of(
            "type", failure.getClass().getName(),
            "message", String.valueOf(failure.getMessage()));
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("order_id", order.orderId());
        context.put("stage", "fulfillment_decision");
        context.put("consumed_tokens", consumedTokens);
        Map<String, Object> payload = Map.of(
            "message", "Checkout agent failed after inference usage",
            "level", "error",
            "exception", exception,
            "context", context);
        postEnvelope(ERRORS_CAPTURE_PATH, payload, operationKey);
    }

    private JsonNode postEnvelope(String path, Object payload, String idempotencyKey) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            try {
                URI endpoint = URI.create(settings.baseUrl()).resolve(path);
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429) {
                    pause(response, attempt);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiRejected(error.path("code").asText("rejected"), error.toString(), response.statusCode());
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Telemetry transport status " + response.statusCode());
                return envelope.path("data");
            } catch (InfraiRejected rejected) {
                throw rejected;
            } catch (Exception transport) {
                last = new IllegalStateException("Telemetry transport failed", transport);
            }
        }
        throw last;
    }

    private static void pause(HttpResponse<?> response, int attempt) throws InterruptedException {
        long seconds = response.headers().firstValue("Retry-After").map(Long::parseLong)
            .orElse(Math.min(8L, 1L << (attempt - 1)));
        Thread.sleep(Duration.ofSeconds(seconds));
    }

    static final class InfraiRejected extends RuntimeException {
        final String code;
        final int status;
        InfraiRejected(String code, String detail, int status) {
            super(detail);
            this.code = code;
            this.status = status;
        }
    }
}
