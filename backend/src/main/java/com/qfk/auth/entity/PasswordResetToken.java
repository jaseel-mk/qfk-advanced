package com.qfk.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="password_reset_tokens")
public class PasswordResetToken {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @ManyToOne(optional=false) @JoinColumn(name="user_id") public UserAccount user;
 @Column(name="token_hash",nullable=false,unique=true,length=64) public String tokenHash;
 @Column(name="expires_at",nullable=false) public Instant expiresAt;
 @Column(name="used_at") public Instant usedAt;
 @Column(name="created_at",nullable=false,updatable=false) public Instant createdAt=Instant.now();
}
