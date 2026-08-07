package com.metacloth.config;

import com.metacloth.domain.entity.User;
import com.metacloth.domain.enums.AuthProvider;
import com.metacloth.domain.enums.UserRole;
import com.metacloth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminInitializer {
    @Value("${ADMIN_EMAIL:admin@metacloth.local}")
    String email;
    @Value("${ADMIN_PASSWORD:ChangeMe_123!}")
    String password;

    @Bean
    CommandLineRunner seed(UserRepository repo, PasswordEncoder enc) {
        return args -> {
            if (!repo.existsByEmailIgnoreCase(email))
                repo.save(User.builder().email(email.toLowerCase()).fullName("MetaCloth Admin").passwordHash(enc.encode(password)).authProvider(AuthProvider.PHONE).role(UserRole.ADMIN).enabled(true).build());
        };
    }
}
