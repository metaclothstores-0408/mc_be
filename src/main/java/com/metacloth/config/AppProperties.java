package com.metacloth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "metacloth")
public record AppProperties(
        Security security,
        Cors cors,
        Otp otp,
        Google google,
        Dtf dtf,
        R2 r2
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

    public record R2(
            String endpoint,
            String accessKeyId,
            String secretAccessKey,
            String bucketName,
            String publicBaseUrl
    ) {
    }
}
