package com.qfk.auth.service;

import com.qfk.auth.entity.RefreshToken;
import com.qfk.auth.entity.UserAccount;
import com.qfk.auth.repository.RefreshTokenRepository;
import com.qfk.common.config.SecurityConfig;
import com.qfk.member.entity.Member;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenServiceTest {
 private static final String SECRET="qfk-test-signing-secret";
 private final SecurityConfig security=new SecurityConfig();
 private final RefreshTokenRepository refreshTokens=mock(RefreshTokenRepository.class);

 private UserAccount user(){
  var user=new UserAccount();user.id=UUID.randomUUID();user.email="member@example.com";
  user.member=new Member();user.member.id=UUID.randomUUID();return user;
 }

 @Test void issuedTokenMatchesConfiguredHmacKeyAndRejectsAnotherKey() throws Exception {
  var user=user();var service=new TokenService(security.jwtEncoder(SECRET),refreshTokens);
  var tokens=service.issue(user);
  var jwt=security.jwtDecoder(SECRET).decode(tokens.accessToken());
  assertEquals("HS256",jwt.getHeaders().get("alg"));
  assertEquals(user.id.toString(),jwt.getSubject());
  assertEquals(user.member.id.toString(),jwt.getClaimAsString("memberId"));
  assertEquals("MEMBER",jwt.getClaimAsString("role"));
  var wrongKeyDecoder=security.jwtDecoder("different-test-secret");
  assertThrows(JwtException.class,()->wrongKeyDecoder.decode(tokens.accessToken()));
  verify(refreshTokens).save(any(RefreshToken.class));
 }

 @Test void rotatingRefreshTokenProducesAnotherVerifiableAccessToken() throws Exception {
  var user=user();var previous=new RefreshToken();previous.user=user;
  previous.expiresAt=Instant.now().plusSeconds(3600);
  when(refreshTokens.findByTokenHashAndRevokedAtIsNull(anyString())).thenReturn(Optional.of(previous));
  var service=new TokenService(security.jwtEncoder(SECRET),refreshTokens);
  var tokens=service.rotate("existing-refresh-token");
  assertNotNull(previous.revokedAt);
  assertEquals(user.id.toString(),security.jwtDecoder(SECRET).decode(tokens.accessToken()).getSubject());
  verify(refreshTokens).save(any(RefreshToken.class));
 }
}
