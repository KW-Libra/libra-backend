package com.libra.api.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByOauthProviderAndOauthProviderId(String provider, String providerId);

    boolean existsByEmail(String email);
}
