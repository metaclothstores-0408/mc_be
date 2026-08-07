package com.metacloth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.metacloth.config.AppProperties;
import com.metacloth.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class GoogleTokenService {
    private final AppProperties properties;

    public Identity verify(String idToken) {
        String clientId = properties.google().clientId();
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google authentication is not configured.");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,
                        "Invalid Google ID token.");
            }

            Payload payload = token.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,
                        "Google email is not verified.");
            }

            String email = payload.getEmail();
            String name = payload.get("name") instanceof String s ? s : "MetaCloth Customer";
            return new Identity(email, name);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Google authentication failed.");
        }
    }

    public record Identity(String email, String name) {
    }
}
