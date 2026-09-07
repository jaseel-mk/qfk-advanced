package com.qfk.match.repository;
import com.qfk.match.entity.MatchRegistration; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RegistrationRepository extends JpaRepository<MatchRegistration,UUID>{long countByMatchIdAndStatus(UUID matchId,MatchRegistration.Status status); boolean existsByMatchIdAndMemberId(UUID matchId,UUID memberId); Optional<MatchRegistration> findFirstByMatchIdAndStatusOrderByRegisteredAtAsc(UUID matchId,MatchRegistration.Status status); java.util.List<MatchRegistration> findByMatchIdOrderByRegisteredAtAsc(UUID matchId);}
