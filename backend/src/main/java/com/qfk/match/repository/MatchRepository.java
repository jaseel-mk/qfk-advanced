package com.qfk.match.repository;
import com.qfk.match.entity.FootballMatch; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType;
public interface MatchRepository extends JpaRepository<FootballMatch,UUID>{@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from FootballMatch m where m.id=:id") Optional<FootballMatch> findByIdForRegistration(@Param("id") UUID id);}
