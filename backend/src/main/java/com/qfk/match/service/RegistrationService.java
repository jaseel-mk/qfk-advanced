package com.qfk.match.service;
import com.qfk.match.entity.*; import com.qfk.match.repository.*; import com.qfk.member.entity.Member; import jakarta.persistence.EntityManager; import java.time.Instant; import java.util.UUID; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class RegistrationService {private final MatchRepository matches;private final RegistrationRepository registrations;private final EntityManager em; public RegistrationService(MatchRepository m,RegistrationRepository r,EntityManager e){matches=m;registrations=r;em=e;}
 @Transactional public MatchRegistration register(UUID matchId,Member member){
  var match=matches.findByIdForRegistration(matchId).orElseThrow(()->new IllegalArgumentException("Match not found"));
  requireOpen(match);
  if(!member.active)throw new IllegalStateException("Member is inactive");
  if(match.status!=FootballMatch.MatchStatus.REGISTRATION_OPEN&&match.status!=FootballMatch.MatchStatus.FULL)throw new IllegalStateException("Registration is not open");
  var r=registrations.findByMatchIdOrderByRegisteredAtAsc(matchId).stream().filter(x->x.member!=null&&x.member.id.equals(member.id)).findFirst().orElseGet(MatchRegistration::new);
  if(r.id!=null&&(r.status==MatchRegistration.Status.CONFIRMED||r.status==MatchRegistration.Status.WAITLIST))throw new IllegalStateException("Member is already registered");
  long confirmed=registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.CONFIRMED);
  r.match=match;r.member=member;r.registeredAt=Instant.now();r.status=confirmed<match.maximumPlayers?MatchRegistration.Status.CONFIRMED:MatchRegistration.Status.WAITLIST;
  r.waitlistPosition=r.status==MatchRegistration.Status.WAITLIST?(int)(registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.WAITLIST)+1):null;
  r.attendanceStatus=MatchRegistration.AttendanceStatus.NOT_MARKED;
  if(confirmed+1>=match.maximumPlayers)match.status=FootballMatch.MatchStatus.FULL;
  return registrations.save(r);
 }
 private void requireOpen(FootballMatch match){if(match.startsAt==null||!match.startsAt.isAfter(Instant.now())||(match.status!=FootballMatch.MatchStatus.REGISTRATION_OPEN&&match.status!=FootballMatch.MatchStatus.FULL))throw new IllegalStateException("Registration is not open");}
 @Transactional public MatchRegistration registerGuest(UUID matchId,String name,String mobile){
  var match=matches.findByIdForRegistration(matchId).orElseThrow(()->new IllegalArgumentException("Match not found"));requireOpen(match);
  String phone=mobile.replaceAll("[\\s()-]","");if(!phone.matches("\\+[1-9][0-9]{6,14}"))throw new IllegalArgumentException("Enter a mobile number with country code, for example +97412345678");
  String clean=name.trim();if(clean.isBlank())throw new IllegalArgumentException("Guest name is required");
  var r=registrations.findByMatchIdOrderByRegisteredAtAsc(matchId).stream().filter(x->phone.equals(x.guestMobile)).findFirst().orElseGet(MatchRegistration::new);
  if(r.id!=null&&(r.status==MatchRegistration.Status.CONFIRMED||r.status==MatchRegistration.Status.WAITLIST))throw new IllegalStateException("This mobile number is already registered for this match");
  long confirmed=registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.CONFIRMED);
  r.match=match;r.member=null;r.guestName=clean;r.guestMobile=phone;r.registeredAt=Instant.now();r.status=confirmed<match.maximumPlayers?MatchRegistration.Status.CONFIRMED:MatchRegistration.Status.WAITLIST;r.waitlistPosition=r.status==MatchRegistration.Status.WAITLIST?(int)(registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.WAITLIST)+1):null;r.attendanceStatus=MatchRegistration.AttendanceStatus.NOT_MARKED;
  if(confirmed+1>=match.maximumPlayers)match.status=FootballMatch.MatchStatus.FULL;return registrations.save(r);
 }
 @Transactional public java.util.List<MatchRegistration> addNamedPlayers(UUID matchId,java.util.List<String> names){
  var match=matches.findByIdForRegistration(matchId).orElseThrow(()->new IllegalArgumentException("Match not found"));
  if(java.util.Set.of(FootballMatch.MatchStatus.IN_PROGRESS,FootballMatch.MatchStatus.COMPLETED,FootballMatch.MatchStatus.CANCELLED).contains(match.status))throw new IllegalStateException("Players cannot be added to a started or cancelled match");
  var seen=new java.util.HashSet<String>();var cleanNames=new java.util.ArrayList<String>();
  for(String name:names){String clean=name.trim();if(clean.isEmpty()||clean.length()>160)throw new IllegalArgumentException("Each player needs a name of at most 160 characters");if(!seen.add(clean.toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Each player name must appear only once in the list");cleanNames.add(clean);}
  var existing=registrations.findByMatchIdOrderByRegisteredAtAsc(matchId);var result=new java.util.ArrayList<MatchRegistration>();
  for(String name:cleanNames)if(existing.stream().anyMatch(x->x.member==null&&x.guestMobile==null&&name.equalsIgnoreCase(x.guestName)&&(x.status==MatchRegistration.Status.CONFIRMED||x.status==MatchRegistration.Status.WAITLIST)))throw new IllegalStateException(name+" is already on this match's player list");
  long confirmed=registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.CONFIRMED),waiting=registrations.countByMatchIdAndStatus(matchId,MatchRegistration.Status.WAITLIST);
  for(String name:cleanNames){var r=existing.stream().filter(x->x.member==null&&x.guestMobile==null&&name.equalsIgnoreCase(x.guestName)).findFirst().orElseGet(MatchRegistration::new);
   if(r.id!=null&&(r.status==MatchRegistration.Status.CONFIRMED||r.status==MatchRegistration.Status.WAITLIST))throw new IllegalStateException(name+" is already on this match's player list");
   r.match=match;r.member=null;r.guestName=name;r.guestMobile=null;r.registeredAt=Instant.now();r.attendanceStatus=MatchRegistration.AttendanceStatus.NOT_MARKED;
   if(confirmed<match.maximumPlayers){r.status=MatchRegistration.Status.CONFIRMED;r.waitlistPosition=null;confirmed++;}else{r.status=MatchRegistration.Status.WAITLIST;r.waitlistPosition=(int)++waiting;}
   result.add(registrations.save(r));
  }
  if(confirmed>=match.maximumPlayers&&match.status==FootballMatch.MatchStatus.REGISTRATION_OPEN)match.status=FootballMatch.MatchStatus.FULL;
  return result;
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
