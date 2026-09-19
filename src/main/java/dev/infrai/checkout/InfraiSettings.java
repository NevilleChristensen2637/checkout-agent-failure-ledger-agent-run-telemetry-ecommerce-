package dev.infrai.checkout;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("infrai")
public record InfraiSettings(String baseUrl, String apiKey, int maxAttempts) {
    public InfraiSettings {
        if (baseUrl == null || apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_BASE_URL and INFRAI_API_KEY are required");
        }
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
    }
}
