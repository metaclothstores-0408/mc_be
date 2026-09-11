package com.metacloth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "metacloth")
public record AppProperties(
        Security security,
        Cors cors,
        Otp otp,
        Google google,
        Dtf dtf
) {

    public record Security(Jwt jwt) {
        public record Jwt(String secret, long expirationMinutes) {
        }
    }

    public record Cors(String allowedOrigins) {
    }

    public record Otp(
            int length,
            long expiryMinutes,
            long resendCooldownSeconds,
            int maxAttempts
    ) {
    }

    public record Google(
            String clientId,
            String clientSecret,
            String redirectUri,
            String frontendCallbackUrl,
            String scopes,
            String tokenEncryptionKey
    ) {
    }

    public record Dtf(
            String outputDir,
            String baseUrl
    ) {
    }
}