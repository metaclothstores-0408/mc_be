package com.metacloth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.metacloth.api.dto.AuthDtos.AuthResponse;
import com.metacloth.config.AppProperties;
import com.metacloth.domain.entity.GoogleOAuthAccount;
import com.metacloth.domain.entity.User;
import com.metacloth.domain.enums.AuthProvider;
import com.metacloth.domain.enums.UserRole;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.GoogleOAuthAccountRepository;
import com.metacloth.repository.UserRepository;
import com.metacloth.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String GOOGLE_PROVIDER = "GOOGLE";

    private static final String AUTHORIZATION_ENDPOINT =
            "https://accounts.google.com/o/oauth2/v2/auth";

    private static final String TOKEN_ENDPOINT =
            "https://oauth2.googleapis.com/token";

    private static final String OAUTH_STATE_COOKIE =
            "MC_GOOGLE_OAUTH_STATE";

    private static final String OAUTH_SESSION_COOKIE =
            "MC_GOOGLE_OAUTH_SESSION";

    private final AppProperties properties;
    private final GoogleTokenService googleTokenService;
    private final GoogleOAuthAccountRepository googleAccounts;
    private final UserRepository users;
    private final JwtService jwtService;
    private final TokenEncryptionService encryption;

    public String startAuthorization(HttpServletResponse response) {

        validateConfiguration();

        String state = generateState();

        Cookie cookie = new Cookie(
                OAUTH_STATE_COOKIE,
                state
        );

        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(600);

        response.addCookie(cookie);

        List<String> scopes = getScopes();

        return UriComponentsBuilder
                .fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam(
                        "client_id",
                        properties.google().clientId()
                )
                .queryParam(
                        "redirect_uri",
                        properties.google().redirectUri()
                )
                .queryParam(
                        "response_type",
                        "code"
                )
                .queryParam(
                        "scope",
                        String.join(" ", scopes)
                )
                .queryParam(
                        "access_type",
                        "offline"
                )
                .queryParam(
                        "include_granted_scopes",
                        "true"
                )
                .queryParam(
                        "prompt",
                        "consent"
                )
                .queryParam(
                        "state",
                        state
                )
                .build()
                .encode()
                .toUriString();
    }

    @Transactional
    public AuthResponse handleCallback(
            String code,
            String state,
            String expectedState,
            HttpServletResponse response
    ) {

        clearCookie(
                response,
                OAUTH_STATE_COOKIE
        );

        if (state == null ||
                expectedState == null ||
                !state.equals(expectedState)) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid OAuth state."
            );
        }

        if (code == null || code.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Google authorization code is missing."
            );
        }

        try {

            GoogleTokenResponse tokenResponse =
                    new GoogleAuthorizationCodeTokenRequest(
                            new NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            properties.google().clientId(),
                            properties.google().clientSecret(),
                            code,
                            properties.google().redirectUri()
                    ).execute();

            String idToken = tokenResponse.getIdToken();

            if (idToken == null || idToken.isBlank()) {
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Google did not return an ID token."
                );
            }

            GoogleTokenService.Identity identity =
                    googleTokenService.verify(idToken);

            User user = findOrCreateUser(identity);

            saveGoogleAccount(
                    user,
                    identity,
                    tokenResponse
            );

            String metaClothJwt =
                    jwtService.generate(user);

            createSessionCookie(
                    response,
                    metaClothJwt
            );

            return new AuthResponse(
                    metaClothJwt,
                    "Bearer",
                    jwtService.expires(),
                    new UserServiceDtoHelper(users).dto(user)
            );

        } catch (ApiException ex) {

            throw ex;

        } catch (Exception ex) {

            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Google OAuth authentication failed."
            );
        }
    }

    private User findOrCreateUser(
            GoogleTokenService.Identity identity
    ) {

        return googleAccounts
                .findByProviderAndProviderSubject(
                        GOOGLE_PROVIDER,
                        identity.subject()
                )
                .map(GoogleOAuthAccount::getUser)
                .orElseGet(() ->
                        users.findByEmailIgnoreCase(
                                identity.email()
                        ).orElseGet(() ->
                                users.save(
                                        User.builder()
                                                .fullName(identity.name())
                                                .email(identity.email().toLowerCase())
                                                .authProvider(AuthProvider.GOOGLE)
                                                .role(UserRole.CUSTOMER)
                                                .enabled(true)
                                                .build()
                                )
                        )
                );
    }

    private void saveGoogleAccount(
            User user,
            GoogleTokenService.Identity identity,
            GoogleTokenResponse tokenResponse
    ) {

        GoogleOAuthAccount account =
                googleAccounts
                        .findByProviderAndProviderSubject(
                                GOOGLE_PROVIDER,
                                identity.subject()
                        )
                        .orElseGet(() ->
                                googleAccounts
                                        .findByUserIdAndProvider(
                                                user.getId(),
                                                GOOGLE_PROVIDER
                                        )
                                        .orElseGet(
                                                GoogleOAuthAccount::new
                                        )
                        );

        account.setUser(user);
        account.setProvider(GOOGLE_PROVIDER);
        account.setProviderSubject(identity.subject());
        account.setProviderEmail(identity.email());

        if (tokenResponse.getAccessToken() != null) {
            account.setAccessTokenEncrypted(
                    encryption.encrypt(
                            tokenResponse.getAccessToken()
                    )
            );
        }

        if (tokenResponse.getRefreshToken() != null) {
            account.setRefreshTokenEncrypted(
                    encryption.encrypt(
                            tokenResponse.getRefreshToken()
                    )
            );
        }

        if (tokenResponse.getExpiresInSeconds() != null) {

            account.setAccessTokenExpiresAt(
                    OffsetDateTime.now(ZoneOffset.UTC)
                            .plusSeconds(
                                    tokenResponse.getExpiresInSeconds()
                            )
            );
        }

        account.setScopes(
                tokenResponse.getScope()
        );

        googleAccounts.save(account);
    }

    private void createSessionCookie(
            HttpServletResponse response,
            String jwt
    ) {

        Cookie cookie = new Cookie(
                OAUTH_SESSION_COOKIE,
                jwt
        );

        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60);

        response.addCookie(cookie);
    }

    public String consumeSessionCookie(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response
    ) {

        String token = null;

        if (request.getCookies() != null) {

            for (Cookie cookie : request.getCookies()) {

                if (OAUTH_SESSION_COOKIE.equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        clearCookie(
                response,
                OAUTH_SESSION_COOKIE
        );

        if (token == null || token.isBlank()) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Google authentication session has expired."
            );
        }

        return token;
    }

    private void clearCookie(
            HttpServletResponse response,
            String name
    ) {

        Cookie cookie = new Cookie(name, "");

        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }

    private String generateState() {

        byte[] bytes = new byte[32];

        new java.security.SecureRandom()
                .nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private List<String> getScopes() {

        return Arrays.stream(
                        properties.google()
                                .scopes()
                                .split(",")
                )
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private void validateConfiguration() {

        if (properties.google().clientId() == null ||
                properties.google().clientId().isBlank() ||
                properties.google().clientSecret() == null ||
                properties.google().clientSecret().isBlank() ||
                properties.google().redirectUri() == null ||
                properties.google().redirectUri().isBlank()) {

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google OAuth is not configured."
            );
        }
    }

    private static class UserServiceDtoHelper {

        private final UserRepository repository;

        UserServiceDtoHelper(UserRepository repository) {
            this.repository = repository;
        }

        com.metacloth.api.dto.AuthDtos.UserResponse dto(User u) {

            return new com.metacloth.api.dto.AuthDtos.UserResponse(
                    u.getId(),
                    u.getFullName(),
                    u.getEmail(),
                    u.getPhoneNumber(),
                    u.getRole().name(),
                    u.getSelectedAvatar() == null
                            ? null
                            : u.getSelectedAvatar().getId()
            );
        }
    }
}