package com.qfk.common.config;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import javax.crypto.spec.SecretKeySpec;import com.nimbusds.jose.JWSAlgorithm;import com.nimbusds.jose.jwk.JWKSet;import com.nimbusds.jose.jwk.OctetSequenceKey;import com.nimbusds.jose.jwk.source.ImmutableJWKSet;import com.nimbusds.jose.proc.SecurityContext;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;import org.springframework.security.config.Customizer;import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.security.oauth2.jose.jws.MacAlgorithm;import org.springframework.security.oauth2.jwt.*;import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;import org.springframework.security.core.authority.SimpleGrantedAuthority;import org.springframework.security.web.SecurityFilterChain;import org.springframework.web.cors.*;
@Configuration @EnableMethodSecurity public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 private SecretKeySpec key(String secret)throws Exception{return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)),"HmacSHA256");}
 @Bean public JwtEncoder jwtEncoder(@Value("${qfk.jwt-secret}") String secret)throws Exception{var jwk=new OctetSequenceKey.Builder(key(secret)).algorithm(JWSAlgorithm.HS256).build();return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));}
 @Bean public JwtDecoder jwtDecoder(@Value("${qfk.jwt-secret}") String secret,org.springframework.jdbc.core.JdbcTemplate db)throws Exception{var decoder=NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> account=jwt->{try{boolean valid=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM user_accounts u JOIN members m ON m.id=u.member_id WHERE u.id=? AND u.role=? AND u.enabled AND m.active)",Boolean.class,java.util.UUID.fromString(jwt.getSubject()),jwt.getClaimAsString("role")));return valid?org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success():org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token","Account access changed",null));}catch(Exception e){return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));}};decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),account));return decoder;}
 @Bean CorsConfigurationSource corsConfigurationSource(@Value("${FRONTEND_URL:http://localhost:5173}") String configuredFrontends){
  var origins=new java.util.LinkedHashSet<String>();
  for(String origin:configuredFrontends.split(",")){String clean=origin.trim().replaceAll("/+$","");if(!clean.isBlank())origins.add(clean);}
  origins.add("https://jaseel-mk.github.io");
  origins.add("https://qfk-qatar.vkdjaseel.chatgpt.site");
  origins.add("http://localhost:5173");
  origins.add("http://127.0.0.1:5173");
  var c=new CorsConfiguration();
  c.setAllowedOrigins(new java.util.ArrayList<>(origins));
  c.setAllowedMethods(java.util.List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
  c.setAllowedHeaders(java.util.List.of("*"));
  c.setExposedHeaders(java.util.List.of("Location"));
  c.setMaxAge(3600L);
  var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",c);return source;
 }
 @Bean JwtAuthenticationConverter jwtAuthenticationConverter(){var converter=new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(jwt->{String role=jwt.getClaimAsString("role");return role==null?java.util.List.of():java.util.List.of(new SimpleGrantedAuthority("ROLE_"+role));});return converter;}
 @Bean SecurityFilterChain filterChain(HttpSecurity http,JwtAuthenticationConverter converter)throws Exception{return http.csrf(c->c.disable()).cors(Customizer.withDefaults()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/api/public/**","/v3/api-docs/**","/swagger-ui/**","/actuator/health").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))).headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'"))).build();}
}
