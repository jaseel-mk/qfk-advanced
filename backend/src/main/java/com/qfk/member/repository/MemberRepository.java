package com.qfk.member.repository;
import com.qfk.member.entity.Member; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberRepository extends JpaRepository<Member,UUID>{}
