package com.qfk.member.entity;
import jakarta.persistence.*; import java.time.*; import java.util.*;
@Entity @Table(name="members") public class Member {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @Column(nullable=false) public String fullName; public String nickname;
 @Column(unique=true) public String email; public String mobile; public String location;
 @Enumerated(EnumType.STRING) public Position preferredPosition;
 @Enumerated(EnumType.STRING) public SkillLevel skillLevel;
 public LocalDate joinedOn; public boolean active=true;
 public enum SkillLevel{BEGINNER,INTERMEDIATE,EXPERIENCED}
 public enum Position{GOALKEEPER,CENTRE_BACK,RIGHT_BACK,LEFT_BACK,DEFENSIVE_MIDFIELDER,CENTRAL_MIDFIELDER,ATTACKING_MIDFIELDER,RIGHT_WING,LEFT_WING,STRIKER,UTILITY}
}
