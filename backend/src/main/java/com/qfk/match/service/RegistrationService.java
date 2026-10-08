package com.qfk.match.service;
import com.qfk.match.entity.*; import com.qfk.match.repository.*; import com.qfk.member.entity.Member; import jakarta.persistence.EntityManager; import java.time.Instant; import java.util.UUID; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class RegistrationService {private final MatchRepository matches;private final RegistrationRepository registrations;private final EntityManager em; public RegistrationService(MatchRepository m,RegistrationRepository r,EntityManager e){matches=m;registrations=r;em=e;}
 @Transactional public MatchRegistration register(UUID matchId,Member member){
  var match=matches.findByIdForRegistration(matchId).orElseThrow(()->new IllegalArgumentException("Match not found"));
  if(!member.active)throw new IllegalStateException("Member is inactive");
  if(match.status!=FootballMatch.MatchStatus.REGISTRATION_OPEN&&match.status!=FootballMatch.MatchStatus.FULL)throw new IllegalStateException("Registration is not open");
  var r=registrations.findByMatchIdOrderByRegisteredAtAsc(matchId).stream().filter(x->x.member.id.equals(member.id)).findFirst().orElseGet(MatchRegistration::new);
  if(r.id!=null&&(r.status==MatchRegistration.Status.CONFIRMED||r.status==MatchRegistration.Status.WAITLIST))throw new IllegalStateException("Member is already registered");
  long confirmed=registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.CONFIRMED);
  r.match=match;r.member=member;r.registeredAt=Instant.now();r.status=confirmed<match.maximumPlayers?MatchRegistration.Status.CONFIRMED:MatchRegistration.Status.WAITLIST;
  r.waitlistPosition=r.status==MatchRegistration.Status.WAITLIST?(int)(registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.WAITLIST)+1):null;
  r.attendanceStatus=MatchRegistration.AttendanceStatus.NOT_MARKED;
  if(confirmed+1>=match.maximumPlayers)match.status=FootballMatch.MatchStatus.FULL;
  return registrations.save(r);
 }
 @Transactional public void cancel(UUID id){
  var initial=registrations.findById(id).orElseThrow(()->new IllegalArgumentException("Registration not found"));
  var match=matches.findByIdForRegistration(initial.match.id).orElseThrow();
  em.refresh(initial);
  if(initial.status!=MatchRegistration.Status.CONFIRMED&&initial.status!=MatchRegistration.Status.WAITLIST)throw new IllegalStateException("Registration is already cancelled");
  if(match.status==FootballMatch.MatchStatus.COMPLETED||match.status==FootballMatch.MatchStatus.IN_PROGRESS)throw new IllegalStateException("Cannot cancel a started match registration");
  boolean promote=initial.status==MatchRegistration.Status.CONFIRMED;initial.status=MatchRegistration.Status.CANCELLED;initial.waitlistPosition=null;
  if(promote)registrations.findFirstByMatchIdAndStatusOrderByRegisteredAtAsc(match.id,MatchRegistration.Status.WAITLIST).ifPresent(next->{next.status=MatchRegistration.Status.CONFIRMED;next.waitlistPosition=null;});
  int position=1;for(var next:registrations.findByMatchIdOrderByRegisteredAtAsc(match.id))if(next.status==MatchRegistration.Status.WAITLIST)next.waitlistPosition=position++;
  if(match.status==FootballMatch.MatchStatus.FULL&&registrations.countByMatchIdAndStatus(match.id,MatchRegistration.Status.CONFIRMED)<match.maximumPlayers)match.status=FootballMatch.MatchStatus.REGISTRATION_OPEN;
 }
}
