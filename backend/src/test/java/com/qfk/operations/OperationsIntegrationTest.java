package com.qfk.operations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={"spring.datasource.url=${QFK_TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/qfk_test}","spring.datasource.username=qfk_test","spring.datasource.password=qfk_test","qfk.mail-enabled=false"})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="QFK_TEST_DATABASE_URL",matches=".+")
class OperationsIntegrationTest {
 @Autowired jakarta.persistence.EntityManager em;@Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 UUID memberId,userId,matchId;
 @BeforeEach void fixture(){memberId=UUID.randomUUID();userId=UUID.randomUUID();matchId=UUID.randomUUID();db.update("INSERT INTO members(id,full_name,joined_on) VALUES(?,'Test Player',current_date)",memberId);db.update("INSERT INTO user_accounts(id,member_id,email,password_hash,role) VALUES(?,?,?,'unused','ADMIN')",userId,memberId,userId+"@test.invalid");db.update("INSERT INTO matches(id,match_number,title,type,status,starts_at,ends_at,maximum_players,registration_fee,venue) VALUES(?,12345,'Test Match','COMMUNITY_MATCH','REGISTRATION_OPEN',now()+interval '1 day',now()+interval '1 day 2 hours',2,10,'Test ground')",matchId);}
 JwtRequestPostProcessor as(String role){return jwt().jwt(j->j.subject(userId.toString()).claim("memberId",memberId.toString()).claim("role",role)).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
 String postJson(String path,String body,String role)throws Exception{return mvc.perform(post(path).with(as(role)).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
 String id(String result)throws Exception{return json.readTree(result).get("id").asText();}
 @Test void adminCanAddNamesWithoutMobileAndOverflowWaitlists()throws Exception{
  mvc.perform(post("/api/matches/"+matchId+"/players").with(as("ADMIN")).contentType("application/json").content(json.writeValueAsString(Map.of("names",List.of(" Ahmed Ali ","Jaseel M","Mohammed K")))))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].memberName").value("Ahmed Ali")).andExpect(jsonPath("$[0].guestMobile").doesNotExist()).andExpect(jsonPath("$[1].status").value("CONFIRMED")).andExpect(jsonPath("$[2].status").value("WAITLIST")).andExpect(jsonPath("$[2].waitlistPosition").value(1));
  em.flush();assertEquals(3,db.queryForObject("SELECT count(*) FROM match_registrations WHERE match_id=? AND member_id IS NULL AND guest_mobile IS NULL",Integer.class,matchId));
  assertEquals("FULL",db.queryForObject("SELECT status FROM matches WHERE id=?",String.class,matchId));
 }
 @Test void namedPlayersRejectUnauthorizedDuplicatesAndStartedMatches()throws Exception{
  String path="/api/matches/"+matchId+"/players";
  for(String role:List.of("MEMBER","ORGANIZER"))mvc.perform(post(path).with(as(role)).contentType("application/json").content("{\"names\":[\"A\"]}")).andExpect(status().isForbidden());
  mvc.perform(post(path).with(as("ADMIN")).contentType("application/json").content("{\"names\":[\"A\",\" a \"]}")).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(as("ADMIN")).contentType("application/json").content("{\"names\":[\" \"]}")).andExpect(status().isBadRequest());
  postJson(path,"{\"names\":[\"Existing\"]}","ADMIN");em.flush();
  mvc.perform(post(path).with(as("ADMIN")).contentType("application/json").content("{\"names\":[\"New\",\"existing\"]}")).andExpect(status().isConflict());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM match_registrations WHERE match_id=?",Integer.class,matchId));
  db.update("UPDATE matches SET status='IN_PROGRESS' WHERE id=?",matchId);em.clear();
  mvc.perform(post(path).with(as("ADMIN")).contentType("application/json").content("{\"names\":[\"Another\"]}")).andExpect(status().isConflict());
  mvc.perform(post("/api/matches/"+matchId+"/guests").with(as("ADMIN")).contentType("application/json").content("{\"fullName\":\"Public guest\"}")).andExpect(status().isBadRequest());
 }
 @Test void customFormationAndEightPlayerTeamsPersist()throws Exception{
  var body=new HashMap<String,Object>(Map.of("starterLimit",8,"homeName","Home","awayName","Away","homeColor","#801735","awayColor","#475463","players",List.of(),"homeFormation","2-2-3","awayFormation","3-2-2"));
  putOk("/api/manage/matches/"+matchId+"/builder",body,"ADMIN");
  mvc.perform(get("/api/manage/matches/"+matchId+"/builder").with(as("ADMIN"))).andExpect(jsonPath("$.starter_limit").value(8)).andExpect(jsonPath("$.home_formation").value("2-2-3")).andExpect(jsonPath("$.away_formation").value("3-2-2"));
  body.put("homeFormation","4-4-2");mvc.perform(put("/api/manage/matches/"+matchId+"/builder").with(as("ADMIN")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  assertEquals("2-2-3",db.queryForObject("SELECT home_formation FROM match_lineup_settings WHERE match_id=?",String.class,matchId));
 }
 @Test void memberCannotReadPrivateOrMutateAdminSections()throws Exception{
  mvc.perform(get("/api/operations/members").with(as("MEMBER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/operations/finance").with(as("ORGANIZER"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/operations/teams").with(as("MEMBER")).contentType("application/json").content("{\"name\":\"Denied Team\",\"shortName\":\"DEN\",\"description\":\"\",\"foundedOn\":\"2026-10-08\",\"active\":true}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/operations/teams")).andExpect(status().isUnauthorized());
 }
 @Test void memberAndTeamRecordsPersistAndMembershipIsSoftRemoved()throws Exception{
  var m=id(postJson("/api/operations/members","{\"fullName\":\"New Player\",\"joinedOn\":\"2026-10-08\",\"active\":true}","ADMIN"));
  var t=id(postJson("/api/operations/teams","{\"name\":\"Test Team\",\"shortName\":\"TT\",\"description\":\"\",\"foundedOn\":\"2026-10-08\",\"active\":true}","ADMIN"));
  postJson("/api/operations/teams/"+t+"/members","{\"memberId\":\""+m+"\",\"role\":\"CAPTAIN\"}","ADMIN");
  mvc.perform(get("/api/operations/teams/"+t+"/members").with(as("MEMBER"))).andExpect(jsonPath("$[0].full_name").value("New Player"));
  mvc.perform(delete("/api/operations/teams/"+t+"/members/"+m).with(as("ADMIN"))).andExpect(status().isOk());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM team_members WHERE team_id=? AND left_at IS NOT NULL",Integer.class,UUID.fromString(t)));
 }
 @Test void financeTotalsExcludeVoidsButKeepHistory()throws Exception{
  var a=id(postJson("/api/operations/finance","{\"kind\":\"INCOME\",\"amount\":100,\"category\":\"Fees\",\"description\":\"Collected fees\",\"occurredOn\":\"2026-10-08\"}","ADMIN"));
  postJson("/api/operations/finance","{\"kind\":\"EXPENSE\",\"amount\":30,\"category\":\"Ground\",\"description\":\"Ground rental\",\"occurredOn\":\"2026-10-08\"}","ADMIN");
  mvc.perform(get("/api/operations/finance").with(as("ADMIN"))).andExpect(jsonPath("$.summary.balance").value(70));
  postJson("/api/operations/finance/"+a+"/void","{\"reason\":\"Duplicate receipt\"}","ADMIN");
  mvc.perform(get("/api/operations/finance").with(as("ADMIN"))).andExpect(jsonPath("$.summary.balance").value(-30)).andExpect(jsonPath("$.entries.length()").value(2));
 }
 @Test void pollsKeepOneVoteAndRejectVotesAfterClosing()throws Exception{
  var p=id(postJson("/api/operations/polls","{\"question\":\"Which ground?\",\"closesAt\":\"2099-01-01T00:00:00Z\",\"options\":[\"Ground A\",\"Ground B\"]}","ADMIN"));
  var options=db.queryForList("SELECT id FROM poll_options WHERE poll_id=? ORDER BY label",UUID.fromString(p));
  postJson("/api/operations/polls/"+p+"/vote","{\"optionId\":\""+options.get(0).get("id")+"\"}","MEMBER");
  postJson("/api/operations/polls/"+p+"/vote","{\"optionId\":\""+options.get(1).get("id")+"\"}","MEMBER");
  assertEquals(1,db.queryForObject("SELECT count(*) FROM poll_votes WHERE poll_id=?",Integer.class,UUID.fromString(p)));
  postJson("/api/operations/polls/"+p+"/close","{}","ADMIN");
  mvc.perform(post("/api/operations/polls/"+p+"/vote").with(as("MEMBER")).contentType("application/json").content("{\"optionId\":\""+options.get(0).get("id")+"\"}")).andExpect(status().isBadRequest());
 }
 @Test void draftsAreHiddenFromMembersAndPublishedAnnouncementsAppear()throws Exception{
  postJson("/api/operations/announcements","{\"title\":\"Draft\",\"body\":\"Private draft\",\"priority\":\"NORMAL\",\"status\":\"DRAFT\"}","ADMIN");
  postJson("/api/operations/announcements","{\"title\":\"Published\",\"body\":\"Hello players\",\"priority\":\"IMPORTANT\",\"status\":\"PUBLISHED\"}","ADMIN");
  mvc.perform(get("/api/operations/announcements").with(as("MEMBER"))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].title").value("Published"));
 }
 @Test void tournamentScoresGenerateStandings()throws Exception{
  var a=id(postJson("/api/operations/teams","{\"name\":\"Team A\",\"shortName\":\"A\",\"description\":\"\",\"foundedOn\":\"2026-10-08\",\"active\":true}","ADMIN"));
  var b=id(postJson("/api/operations/teams","{\"name\":\"Team B\",\"shortName\":\"B\",\"description\":\"\",\"foundedOn\":\"2026-10-08\",\"active\":true}","ADMIN"));
  var t=id(postJson("/api/operations/tournaments","{\"name\":\"Test Cup\",\"startsOn\":\"2026-10-08\",\"endsOn\":\"2026-10-10\",\"venue\":\"Doha\",\"status\":\"OPEN\",\"description\":\"\"}","ADMIN"));
  for(String team:List.of(a,b))postJson("/api/operations/tournaments/"+t+"/teams","{\"teamId\":\""+team+"\"}","ADMIN");
  var f=id(postJson("/api/operations/tournaments/"+t+"/fixtures","{\"homeTeamId\":\""+a+"\",\"awayTeamId\":\""+b+"\",\"startsAt\":\"2026-10-09T17:00:00Z\"}","ADMIN"));
  mvc.perform(put("/api/operations/fixtures/"+f+"/score").with(as("ADMIN")).contentType("application/json").content("{\"homeScore\":3,\"awayScore\":1}")).andExpect(status().isOk());
  mvc.perform(get("/api/operations/tournaments/"+t+"/standings").with(as("MEMBER"))).andExpect(jsonPath("$[0].name").value("Team A")).andExpect(jsonPath("$[0].points").value(3)).andExpect(jsonPath("$[1].lost").value(1));
 }
 @Test void matchRegistrationAndPlayerStatisticsPersist()throws Exception{
  postJson("/api/matches/"+matchId+"/registrations","{}","MEMBER");em.flush();
  mvc.perform(put("/api/operations/statistics").with(as("ADMIN")).contentType("application/json").content("{\"matchId\":\""+matchId+"\",\"memberId\":\""+memberId+"\",\"goals\":2,\"assists\":1}")).andExpect(status().isOk());
  mvc.perform(get("/api/operations/statistics").with(as("MEMBER"))).andExpect(jsonPath("$[0].goals").value(2));
  var registration=db.queryForObject("SELECT id FROM match_registrations WHERE match_id=?",UUID.class,matchId);
  mvc.perform(delete("/api/matches/registrations/"+registration).with(as("MEMBER"))).andExpect(status().isOk());
  postJson("/api/matches/"+matchId+"/registrations","{}","MEMBER");em.flush();
  assertEquals(1,db.queryForObject("SELECT count(*) FROM match_registrations WHERE match_id=?",Integer.class,matchId));
 }

 @Test void guestSharesMemberCapacityAndContactIsPrivate()throws Exception{
  postJson("/api/matches/"+matchId+"/registrations","{}","MEMBER");em.flush();
  String a=mvc.perform(post("/api/public/matches/"+matchId+"/guests").contentType("application/json").content("{\"fullName\":\"Guest One\",\"mobile\":\"+974 1234 5678\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andReturn().getResponse().getContentAsString();em.flush();
  mvc.perform(post("/api/public/matches/"+matchId+"/guests").contentType("application/json").content("{\"fullName\":\"Guest Two\",\"mobile\":\"+97412345679\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITLIST"));em.flush();
  mvc.perform(post("/api/public/matches/"+matchId+"/guests").contentType("application/json").content("{\"fullName\":\"Duplicate\",\"mobile\":\"+97412345678\"}")).andExpect(status().isConflict());
  mvc.perform(get("/api/matches/"+matchId+"/registrations").with(as("MEMBER"))).andExpect(jsonPath("$[1].guestMobile").isEmpty());
  mvc.perform(get("/api/matches/"+matchId+"/registrations").with(as("ADMIN"))).andExpect(jsonPath("$[1].guestMobile").value("+97412345678"));
  mvc.perform(delete("/api/matches/registrations/"+id(a)).with(as("MEMBER"))).andExpect(status().isForbidden());
  mvc.perform(delete("/api/matches/registrations/"+id(a)).with(as("ADMIN"))).andExpect(status().isOk());em.flush();
  assertEquals("CONFIRMED",db.queryForObject("SELECT status FROM match_registrations WHERE guest_mobile='+97412345679'",String.class));
 }
 @Test void guestCannotReadRosterOrJoinClosedMatch()throws Exception{
  mvc.perform(get("/api/matches/"+matchId+"/registrations")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/public/matches")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(matchId.toString()));
  mvc.perform(post("/api/public/matches/"+matchId+"/guests").contentType("application/json").content("{\"fullName\":\"Guest\",\"mobile\":\"123\"}")).andExpect(status().isBadRequest());
  db.update("UPDATE matches SET status='REGISTRATION_CLOSED' WHERE id=?",matchId);em.clear();
  mvc.perform(post("/api/public/matches/"+matchId+"/guests").contentType("application/json").content("{\"fullName\":\"Guest\",\"mobile\":\"+97412345670\"}")).andExpect(status().isConflict());
 }

 void putOk(String path,Object body,String role)throws Exception{mvc.perform(put(path).with(as(role)).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());}
 UUID team(String name){UUID id=UUID.randomUUID();db.update("INSERT INTO teams(id,name,short_name,founded_on) VALUES(?,?,?,current_date)",id,name,id.toString().substring(0,8));return id;}
 UUID tournament(){UUID id=UUID.randomUUID();db.update("INSERT INTO tournaments(id,name,starts_on,ends_on,venue,status) VALUES(?,'Planner test','2099-01-01','2099-01-05','Doha','OPEN')",id);return id;}
 @Test void commandCenterPersistsGuestLineupResultAndStatistics()throws Exception{
  UUID registration=UUID.fromString(id(postJson("/api/matches/"+matchId+"/guests","{\"fullName\":\"Matchday guest\",\"mobile\":\"+97400000088\"}","ADMIN")));em.flush();
  putOk("/api/manage/matches/"+matchId+"/lineup/"+registration,Map.of("side","HOME","position","FORWARD","starter",true),"ADMIN");
  putOk("/api/manage/matches/"+matchId+"/statistics/"+registration,Map.of("goals",2,"assists",1),"ADMIN");
  putOk("/api/manage/matches/"+matchId+"/status",Map.of("status","REGISTRATION_CLOSED"),"ADMIN");
  putOk("/api/manage/matches/"+matchId+"/status",Map.of("status","IN_PROGRESS"),"ADMIN");
  putOk("/api/manage/matches/"+matchId+"/result",Map.of("homeScore",2,"awayScore",1),"ADMIN");
  putOk("/api/manage/matches/"+matchId+"/status",Map.of("status","COMPLETED"),"ADMIN");
  mvc.perform(get("/api/manage/matches/"+matchId).with(as("ADMIN"))).andExpect(jsonPath("$.match.home_score").value(2)).andExpect(jsonPath("$.roster[0].goals").value(2)).andExpect(jsonPath("$.roster[0].side").value("HOME"));
  mvc.perform(get("/api/manage/statistics").with(as("MEMBER"))).andExpect(status().isOk()).andExpect(jsonPath("$.guests[0].goals").value(2));
  mvc.perform(put("/api/manage/matches/"+matchId+"/status").with(as("ADMIN")).contentType("application/json").content("{\"status\":\"REGISTRATION_OPEN\"}")).andExpect(status().isConflict());
 }
 @Test void balancingIncludesMembersAndGuestsAndPreservesCapacity()throws Exception{
  postJson("/api/matches/"+matchId+"/registrations","{}","MEMBER");
  postJson("/api/matches/"+matchId+"/guests","{\"fullName\":\"Balanced guest\",\"mobile\":\"+97400000089\"}","ADMIN");em.flush();
  postJson("/api/manage/matches/"+matchId+"/balance","{}","ADMIN");
  assertEquals(1,db.queryForObject("SELECT count(*) FROM match_lineups WHERE side='HOME'",Integer.class));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM match_lineups WHERE side='AWAY'",Integer.class));
 }
 @Test void roleChangesAreAdminOnlyAuditedAndCannotChangeSelf()throws Exception{
  UUID m=UUID.randomUUID(),u=UUID.randomUUID();db.update("INSERT INTO members(id,full_name,joined_on) VALUES(?,'Other member',current_date)",m);db.update("INSERT INTO user_accounts(id,member_id,email,password_hash,role) VALUES(?,?,?,'unused','MEMBER')",u,m,u+"@test.invalid");
  mvc.perform(put("/api/manage/members/"+m+"/role").with(as("MEMBER")).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
  putOk("/api/manage/members/"+m+"/role",Map.of("role","ORGANIZER"),"ADMIN");
  assertEquals("ORGANIZER",db.queryForObject("SELECT role FROM user_accounts WHERE id=?",String.class,u));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM account_role_history WHERE account_id=?",Integer.class,u));
  mvc.perform(put("/api/manage/members/"+memberId+"/role").with(as("ADMIN")).contentType("application/json").content("{\"role\":\"MEMBER\"}")).andExpect(status().isConflict());
 }
 @Test void feeReportsAndReceiptsArePrivateAndDoNotDoubleCountIncome()throws Exception{
  postJson("/api/matches/"+matchId+"/registrations","{}","MEMBER");em.flush();
  mvc.perform(get("/api/manage/finance/fees").with(as("MEMBER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/manage/finance/fees").with(as("ADMIN"))).andExpect(jsonPath("$[0].outstanding").value(10));
  var receipt=id(postJson("/api/operations/finance",json.writeValueAsString(Map.of("kind","INCOME","amount",10,"category","Fee","description","Manual collection","occurredOn","2026-10-08","memberId",memberId,"matchId",matchId)),"ADMIN"));
  mvc.perform(get("/api/manage/finance/"+receipt+"/receipt").with(as("ADMIN"))).andExpect(jsonPath("$.amount").value(10));
  mvc.perform(get("/api/manage/finance/members/"+memberId).with(as("ADMIN"))).andExpect(jsonPath("$.length()").value(1));
 }
 @Test void scheduledTeamPollDraftCanBeEditedButOnlyEligibleMembersVote()throws Exception{
  UUID t=team("Poll team");var body=new HashMap<String,Object>(Map.of("question","Draft","status","DRAFT","opensAt","2098-01-01T00:00:00Z","closesAt","2099-01-01T00:00:00Z","options",List.of("A","B"),"eligibleTeamId",t));
  UUID p=UUID.fromString(id(postJson("/api/operations/polls",json.writeValueAsString(body),"ADMIN")));
  mvc.perform(get("/api/operations/polls").with(as("MEMBER"))).andExpect(jsonPath("$.length()").value(0));body.put("status","OPEN");body.put("question","Published");putOk("/api/operations/polls/"+p,body,"ADMIN");
  UUID option=db.queryForObject("SELECT id FROM poll_options WHERE poll_id=? AND label='A'",UUID.class,p);
  mvc.perform(post("/api/operations/polls/"+p+"/vote").with(as("MEMBER")).contentType("application/json").content(json.writeValueAsString(Map.of("optionId",option)))).andExpect(status().isBadRequest());
  db.update("UPDATE polls SET opens_at=now()-interval '1 minute' WHERE id=?",p);db.update("INSERT INTO team_members(team_id,member_id) VALUES(?,?)",t,memberId);
  postJson("/api/operations/polls/"+p+"/vote",json.writeValueAsString(Map.of("optionId",option)),"MEMBER");
  assertEquals(1,db.queryForObject("SELECT count(*) FROM poll_votes WHERE poll_id=?",Integer.class,p));
 }
 @Test void targetedScheduledAnnouncementsProducePrivateReadReceipts()throws Exception{
  UUID t=team("Announcement team");UUID a=UUID.fromString(id(postJson("/api/operations/announcements",json.writeValueAsString(Map.of("title","Team news","body","Only this squad","priority","IMPORTANT","status","PUBLISHED","publishAt","2099-01-01T00:00:00Z","audienceTeamId",t)),"ADMIN")));
  mvc.perform(get("/api/manage/notifications").with(as("MEMBER"))).andExpect(jsonPath("$.length()").value(0));db.update("UPDATE announcements SET publish_at=now()-interval '1 minute' WHERE id=?",a);
  mvc.perform(get("/api/manage/notifications").with(as("MEMBER"))).andExpect(jsonPath("$.length()").value(0));db.update("INSERT INTO team_members(team_id,member_id) VALUES(?,?)",t,memberId);
  mvc.perform(get("/api/manage/notifications").with(as("MEMBER"))).andExpect(jsonPath("$[0].title").value("Team news"));postJson("/api/manage/notifications/"+a+"/read","{}","MEMBER");
  mvc.perform(get("/api/manage/notifications").with(as("MEMBER"))).andExpect(jsonPath("$[0].read_at").isNotEmpty());
 }
 @Test void generatedKnockoutAdvancesOnlyAfterEveryResult()throws Exception{
  UUID t=tournament();for(int i=0;i<4;i++)db.update("INSERT INTO tournament_teams(tournament_id,team_id) VALUES(?,?)",t,team("Knockout "+i));
  postJson("/api/manage/tournaments/"+t+"/generate","{\"format\":\"KNOCKOUT\",\"startsAt\":\"2099-01-01T17:00:00Z\",\"intervalMinutes\":60}","ADMIN");
  mvc.perform(post("/api/manage/tournaments/"+t+"/advance").with(as("ADMIN")).contentType("application/json").content("{\"startsAt\":\"2099-01-02T17:00:00Z\",\"intervalMinutes\":60,\"qualifiersPerGroup\":1}")).andExpect(status().isConflict());
  db.update("UPDATE tournament_fixtures SET home_score=2,away_score=1 WHERE tournament_id=?",t);
  postJson("/api/manage/tournaments/"+t+"/advance","{\"startsAt\":\"2099-01-02T17:00:00Z\",\"intervalMinutes\":60,\"qualifiersPerGroup\":1}","ADMIN");
  assertEquals(3,db.queryForObject("SELECT count(*) FROM tournament_fixtures WHERE tournament_id=?",Integer.class,t));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM tournament_fixtures WHERE tournament_id=? AND round_number=2",Integer.class,t));
  UUID semifinal=db.queryForObject("SELECT id FROM tournament_fixtures WHERE tournament_id=? AND round_number=1 LIMIT 1",UUID.class,t);
  mvc.perform(put("/api/operations/fixtures/"+semifinal+"/score").with(as("ADMIN")).contentType("application/json").content("{\"homeScore\":0,\"awayScore\":3}")).andExpect(status().isConflict());
 }
 @Test void groupsGenerateOnlyIntraGroupGamesAndAdvanceQualifiers()throws Exception{
  UUID t=tournament();for(int i=0;i<4;i++){UUID team=team("Group "+i);db.update("INSERT INTO tournament_teams(tournament_id,team_id,group_name) VALUES(?,?,?)",t,team,i<2?"A":"B");}
  postJson("/api/manage/tournaments/"+t+"/generate","{\"format\":\"GROUP\",\"startsAt\":\"2099-01-01T17:00:00Z\",\"intervalMinutes\":60}","ADMIN");assertEquals(2,db.queryForObject("SELECT count(*) FROM tournament_fixtures WHERE tournament_id=?",Integer.class,t));
  db.update("UPDATE tournament_fixtures SET home_score=1,away_score=0 WHERE tournament_id=?",t);
  postJson("/api/manage/tournaments/"+t+"/advance","{\"startsAt\":\"2099-01-02T17:00:00Z\",\"intervalMinutes\":60,\"qualifiersPerGroup\":1}","ADMIN");assertEquals(3,db.queryForObject("SELECT count(*) FROM tournament_fixtures WHERE tournament_id=?",Integer.class,t));
 }
 @Test void lineupBuilderPersistsPositionsColorsAndRejectsDuplicates()throws Exception{
  UUID registration=UUID.fromString(id(postJson("/api/matches/"+matchId+"/guests","{\"fullName\":\"Builder guest\",\"mobile\":\"+97400000090\"}","ADMIN")));em.flush();
  var player=Map.of("registrationId",registration,"side","HOME","position","FORWARD","starter",true,"x",22.5,"y",18.5,"shirtNumber",9);
  var body=new HashMap<String,Object>(Map.of("starterLimit",5,"homeName","Maroon","awayName","White","homeColor","#801735","awayColor","#475463","players",List.of(player)));
  putOk("/api/manage/matches/"+matchId+"/builder",body,"ADMIN");
  mvc.perform(get("/api/manage/matches/"+matchId).with(as("ADMIN"))).andExpect(jsonPath("$.roster[0].pitch_x").value(22.5)).andExpect(jsonPath("$.roster[0].shirt_number").value(9));
  mvc.perform(get("/api/manage/matches/"+matchId+"/builder").with(as("ADMIN"))).andExpect(jsonPath("$.starter_limit").value(5)).andExpect(jsonPath("$.home_name").value("Maroon"));
  body.put("players",List.of(player,player));mvc.perform(put("/api/manage/matches/"+matchId+"/builder").with(as("ADMIN")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/manage/matches/"+matchId+"/builder").with(as("MEMBER"))).andExpect(status().isForbidden());
 }
 @Test void lineupBuilderRejectsOverCapacityWithoutPartialWrites()throws Exception{
  var players=new ArrayList<Map<String,Object>>();for(int n=0;n<6;n++){UUID m=UUID.randomUUID(),r=UUID.randomUUID();db.update("INSERT INTO members(id,full_name,joined_on) VALUES(?,'Builder player',current_date)",m);db.update("INSERT INTO match_registrations(id,match_id,member_id,status,registered_at) VALUES(?,?,?,'CONFIRMED',now())",r,matchId,m);players.add(Map.of("registrationId",r,"side","HOME","position","UTILITY","starter",true,"x",50,"y",50,"shirtNumber",n+1));}
  var body=Map.of("starterLimit",5,"homeName","Home","awayName","Away","homeColor","#801735","awayColor","#475463","players",players);
  mvc.perform(put("/api/manage/matches/"+matchId+"/builder").with(as("ADMIN")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());assertEquals(0,db.queryForObject("SELECT count(*) FROM match_lineups",Integer.class));
 }


 @Test void namedPositionShortFormsPersistAndDuplicateBaseNamesAreRejected()throws Exception{
  String path="/api/matches/"+matchId+"/players";
  postJson(path,json.writeValueAsString(Map.of("names",List.of("Keeper gk","Defender - DF","Midfielder (MD)","Striker, fd","Flexible Player"))),"ADMIN");em.flush();
  for(var entry:Map.of("Keeper","GOALKEEPER","Defender","DEFENDER","Midfielder","MIDFIELDER","Striker","FORWARD","Flexible Player","UTILITY").entrySet())assertEquals(entry.getValue(),db.queryForObject("SELECT preferred_position FROM match_registrations WHERE match_id=? AND guest_name=?",String.class,matchId,entry.getKey()));
  mvc.perform(get("/api/manage/matches/"+matchId).with(as("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.roster[0].preferred_position").value("GOALKEEPER"));
  mvc.perform(post(path).with(as("ADMIN")).contentType("application/json").content(json.writeValueAsString(Map.of("names",List.of("Same GK","same DF"))))).andExpect(status().isBadRequest());
  assertEquals(5,db.queryForObject("SELECT count(*) FROM match_registrations WHERE match_id=?",Integer.class,matchId));
 }
}
