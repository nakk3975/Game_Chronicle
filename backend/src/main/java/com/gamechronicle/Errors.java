package com.gamechronicle;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> expected(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",Map.of("code","REQUEST_REJECTED","message",e.getReason()==null?"요청을 처리할 수 없습니다.":e.getReason())));}
 @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class}) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("error",Map.of("code","INVALID_INPUT","message","입력값을 확인해 주세요.")));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e){String trace=UUID.randomUUID().toString();org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed [{}]: {}",trace,e.getClass().getSimpleName());return ResponseEntity.internalServerError().body(Map.of("error",Map.of("code","INTERNAL_ERROR","message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.","traceId",trace)));}
}
