package com.uitopic.rumbo.navigation.interfaces;
import com.uitopic.rumbo.navigation.domain.NavigationValidationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.Map;
@RestControllerAdvice(assignableTypes=NavigationController.class)
public class NavigationExceptionHandler {
  @ExceptionHandler({NavigationValidationException.class,IllegalArgumentException.class})
  public ResponseEntity<Map<String,String>> invalid(RuntimeException e) {
    return ResponseEntity.unprocessableEntity().body(Map.of("error",e.getMessage()));
  }
  @ExceptionHandler({MethodArgumentNotValidException.class,MethodArgumentTypeMismatchException.class})
  public ResponseEntity<Map<String,String>> badRequest(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("error","Invalid or missing request field"));
  }
}
