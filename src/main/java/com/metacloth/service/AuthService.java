package com.metacloth.service;

import com.metacloth.api.dto.AuthDtos.AuthResponse;
import com.metacloth.api.dto.AuthDtos.OtpVerifyRequest;
import com.metacloth.api.dto.AuthDtos.SigninRequest;
import com.metacloth.api.dto.AuthDtos.SignupRequest;
import com.metacloth.domain.entity.User;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.UserRepository;
import com.metacloth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserService users;
    private final UserRepository repo;
    private final OtpService otp;
    private final GoogleTokenService google;
    private final JwtService jwt;
    private final PasswordEncoder encoder;

    @Transactional
    public AuthResponse signup(SignupRequest r) {
        return token(users.signup(r.fullName(), r.email(), r.password()));
    }

    public AuthResponse signin(SigninRequest r) {
        User u = repo.findByEmailIgnoreCase(r.email()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (!u.isEnabled() || u.getPasswordHash() == null || !encoder.matches(r.password(), u.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        return token(u);
    }

    @Transactional
    public AuthResponse otp(OtpVerifyRequest r) {
        otp.verify(r.phoneNumber(), r.otp());
        return token(users.phoneUser(otp.normalize(r.phoneNumber())));
    }

    @Transactional
    public AuthResponse google(String idToken) {
        var identity = google.verify(idToken);

        return token(
                users.googleUser(
                        identity.email(),
                        identity.name()
                )
        );
    }

    private AuthResponse token(User u) {
        return new AuthResponse(jwt.generate(u), "Bearer", jwt.expires(), users.dto(u));
    }
}
