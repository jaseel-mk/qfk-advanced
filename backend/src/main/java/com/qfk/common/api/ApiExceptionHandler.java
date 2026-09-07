package com.qfk.common.api;
import java.time.Instant; import java.util.Map; import java.util.stream.Collectors;
import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex){var fields=ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(e->e.getField(),e->e.getDefaultMessage()==null?"Invalid value":e.getDefaultMessage(),(a,b)->a));return ResponseEntity.badRequest().body(new ApiError(Instant.now(),400,"VALIDATION_ERROR","Validation failed",fields));}
 @ExceptionHandler(IllegalStateException.class) ResponseEntity<ApiError> conflict(IllegalStateException ex){return ResponseEntity.status(409).body(new ApiError(Instant.now(),409,"CONFLICT",ex.getMessage(),Map.of()));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiError> badRequest(IllegalArgumentException ex){return ResponseEntity.badRequest().body(new ApiError(Instant.now(),400,"BAD_REQUEST",ex.getMessage(),Map.of()));}
 public record ApiError(Instant timestamp,int status,String code,String message,Map<String,String> fieldErrors){}
}
