package com.metacloth.service;

import com.metacloth.api.dto.AuthDtos.UserResponse;
import com.metacloth.api.dto.AuthDtos.UpdateProfileRequest;
import com.metacloth.domain.entity.User;
import com.metacloth.domain.enums.AuthProvider;
import com.metacloth.domain.enums.UserRole;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;

    @Transactional
    public User signup(String name, String email, String password) {
        if (repo.existsByEmailIgnoreCase(email))
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered.");
        return repo.save(User.builder().fullName(name).email(email.toLowerCase().trim()).passwordHash(encoder.encode(password)).authProvider(AuthProvider.PHONE).role(UserRole.CUSTOMER).enabled(true).build());
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {

        User user = customer(userId);

        String fullName = request.fullName().trim();
        String phoneNumber = request.phoneNumber().trim();

        if (fullName.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Full name is required."
            );
        }

        if (!phoneNumber.matches("^[0-9]{10}$")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Phone number must contain exactly 10 digits."
            );
        }

        if (repo.existsByPhoneNumberAndIdNot(phoneNumber, userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Phone number is already registered."
            );
        }

        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);

        return repo.save(user);
    }

    @Transactional
    public User phoneUser(String phone) {
        return repo.findByPhoneNumber(phone).orElseGet(() -> repo.save(User.builder().phoneNumber(phone).authProvider(AuthProvider.PHONE).role(UserRole.CUSTOMER).enabled(true).build()));
    }

    @Transactional
    public User googleUser(String email, String name) {
        return repo.findByEmailIgnoreCase(email).orElseGet(() -> repo.save(User.builder().email(email.toLowerCase()).fullName(name).authProvider(AuthProvider.GOOGLE).role(UserRole.CUSTOMER).enabled(true).build()));
    }

    public User customer(Long id) {
        User u = repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getRole() != UserRole.CUSTOMER) throw new ApiException(HttpStatus.FORBIDDEN, "Customer access required");
        return u;
    }

    public UserResponse dto(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getRole().name(), u.getSelectedAvatar() == null ? null : u.getSelectedAvatar().getId());
    }
}
