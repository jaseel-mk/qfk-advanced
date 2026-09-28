package com.qfk.auth.service;

import com.qfk.auth.entity.PasswordResetToken;
import com.qfk.auth.repository.PasswordResetTokenRepository;
import com.qfk.auth.repository.RefreshTokenRepository;
import com.qfk.auth.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {
 private static final Logger log=LoggerFactory.getLogger(PasswordResetService.class);
 private final UserAccountRepository users; private final PasswordResetTokenRepository resets; private final RefreshTokenRepository refreshTokens; private final PasswordEncoder passwords; private final JavaMailSender mail;
 @Value("${qfk.frontend-url}") String frontendUrl; @Value("${qfk.mail-enabled:false}") boolean mailEnabled; @Value("${qfk.mail-from}") String mailFrom;
 public PasswordResetService(UserAccountRepository u,PasswordResetTokenRepository r,RefreshTokenRepository f,PasswordEncoder p,JavaMailSender m){users=u;resets=r;refreshTokens=f;passwords=p;mail=m;}
 @Transactional public void request(String email){users.findByEmailIgnoreCase(email.trim()).filter(u->u.enabled).ifPresent(user->{resets.deleteByUser(user);String raw=newToken();var reset=new PasswordResetToken();reset.user=user;reset.tokenHash=hash(raw);reset.expiresAt=Instant.now().plus(30,ChronoUnit.MINUTES);resets.save(reset);String link=UriComponentsBuilder.fromUriString(frontendUrl.replaceAll("/+$","")).queryParam("resetToken",raw).build().toUriString();if(mailEnabled)send(user.email,link);else log.warn("Password reset email requested but MAIL_ENABLED is false; configure SMTP to deliver reset links.");});}
 @Transactional public void reset(String token,String newPassword){var reset=resets.findByTokenHash(hash(token)).filter(r->r.usedAt==null&&r.expiresAt.isAfter(Instant.now())).orElseThrow(()->new IllegalArgumentException("This reset link is invalid or has expired"));reset.user.passwordHash=passwords.encode(newPassword);reset.user.updatedAt=Instant.now();reset.usedAt=Instant.now();refreshTokens.deleteByUser(reset.user);}
 private void send(String to,String link){var message=new SimpleMailMessage();message.setFrom(mailFrom);message.setTo(to);message.setSubject("Reset your QFK password");message.setText("Use this secure link to reset your Qatar Football Koottam password. It expires in 30 minutes and can be used once:\n\n"+link+"\n\nIf you did not request this, you can ignore this email.");mail.send(message);}
 private String newToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 private String hash(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to secure reset token",e);}}
}
