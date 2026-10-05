package com.qfk.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResetEmailSender {
 private static final Logger log=LoggerFactory.getLogger(ResetEmailSender.class);
 private final ObjectMapper json;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 @Value("${qfk.brevo-api-key:}") String apiKey;
 public ResetEmailSender(ObjectMapper json){this.json=json;}
 public void send(String to,String from,String link){
  if(apiKey.isBlank())throw new IllegalStateException("Password reset email is temporarily unavailable. Please contact QFK support.");
  try{
   var body=Map.of("sender",Map.of("name","QFK","email",from),"to",List.of(Map.of("email",to)),"subject","Reset your QFK password","textContent","Use this secure link to reset your Qatar Football Koottam password. It expires in 30 minutes and can be used once:\n\n"+link+"\n\nIf you did not request this, you can ignore this email.");
   var request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
    .timeout(Duration.ofSeconds(15)).header("api-key",apiKey).header("Content-Type","application/json")
    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
   var response=client.send(request,HttpResponse.BodyHandlers.discarding());
   if(response.statusCode()!=201){log.warn("Reset email provider rejected request: HTTP {}",response.statusCode());throw new IllegalStateException("Password reset email is temporarily unavailable. Please try again later.");}
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Password reset email was interrupted. Please try again later.");}
  catch(java.io.IOException e){log.warn("Reset email provider connection failed ({})",e.getClass().getSimpleName());throw new IllegalStateException("Password reset email is temporarily unavailable. Please try again later.");}
 }
}
