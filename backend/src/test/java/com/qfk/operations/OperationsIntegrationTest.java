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
  db.update("UPDATE matches SET status='REGISTRATION_CLOSED' WHERE id=?",matchId);
  mvc.perform(post("/api/public/matches/"+matchId+"/guests").contentType("application/json").content("{\"fullName\":\"Guest\",\"mobile\":\"+97412345670\"}")).andExpect(status().isConflict());
 }
}
