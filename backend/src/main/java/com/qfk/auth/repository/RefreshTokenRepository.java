package com.qfk.auth.repository;
import com.qfk.auth.entity.RefreshToken; import com.qfk.auth.entity.UserAccount; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID>{Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);void deleteByUser(UserAccount user);}
