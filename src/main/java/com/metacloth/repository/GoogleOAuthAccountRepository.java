package com.metacloth.repository;

import com.metacloth.domain.entity.GoogleOAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleOAuthAccountRepository
        extends JpaRepository<GoogleOAuthAccount, Long> {

    Optional<GoogleOAuthAccount> findByProviderAndProviderSubject(
            String provider,
            String providerSubject
    );

    Optional<GoogleOAuthAccount> findByUserIdAndProvider(
            Long userId,
            String provider
    );
}