package com.qfk.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="refresh_tokens")
public class RefreshToken {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @ManyToOne(optional=false) @JoinColumn(name="user_id") public UserAccount user;
 @Column(name="token_hash",nullable=false,unique=true,length=64) public String tokenHash;
 @Column(nullable=false) public Instant expiresAt;
 public Instant revokedAt;
 @Column(nullable=false,updatable=false) public Instant createdAt=Instant.now();
}
