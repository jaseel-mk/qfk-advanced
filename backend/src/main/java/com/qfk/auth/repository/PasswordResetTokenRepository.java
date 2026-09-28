package com.qfk.auth.repository;

import com.qfk.auth.entity.PasswordResetToken;
import com.qfk.auth.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,UUID> {
 Optional<PasswordResetToken> findByTokenHash(String tokenHash);
 void deleteByUser(UserAccount user);
}
