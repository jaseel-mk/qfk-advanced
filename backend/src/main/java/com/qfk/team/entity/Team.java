package com.qfk.team.entity;
import jakarta.persistence.*; import java.time.LocalDate; import java.util.UUID;
@Entity @Table(name="teams") public class Team {@Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id; @Column(nullable=false,unique=true) public String name; @Column(nullable=false,unique=true) public String shortName; public String description; public String primaryColor; public String secondaryColor; public LocalDate foundedOn; public boolean active=true;}
