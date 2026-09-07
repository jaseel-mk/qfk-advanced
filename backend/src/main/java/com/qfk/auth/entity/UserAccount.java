package com.qfk.auth.entity;

import com.qfk.member.entity.Member;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="user_accounts")
public class UserAccount {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @OneToOne(optional=false) @JoinColumn(name="member_id",unique=true) public Member member;
 @Column(nullable=false,unique=true) public String email;
 @Column(name="password_hash",nullable=false) public String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false) public Role role=Role.MEMBER;
 public boolean enabled=true;
 @Column(nullable=false,updatable=false) public Instant createdAt=Instant.now();
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 public enum Role{MEMBER,ORGANIZER,ADMIN,SUPER_ADMIN}
}
