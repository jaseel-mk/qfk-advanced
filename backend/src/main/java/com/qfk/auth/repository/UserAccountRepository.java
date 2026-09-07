package com.qfk.auth.repository;
import com.qfk.auth.entity.UserAccount; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UserAccountRepository extends JpaRepository<UserAccount,UUID>{Optional<UserAccount> findByEmailIgnoreCase(String email);boolean existsByEmailIgnoreCase(String email);}
