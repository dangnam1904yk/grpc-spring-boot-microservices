package com.project.apigateway.exceptions;

import java.nio.file.AccessDeniedException;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import com.project.apigateway.model.app.AppResponse;
import com.project.apigateway.model.app.ErrorResponse;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<AppResponse<ErrorResponse>> handleExecptions(RuntimeException exception) {
        log.error("Exception: ", exception);
        AppResponse appResponse = new AppResponse<>();
        appResponse.setStatus(HttpStatus.BAD_REQUEST.value());
        appResponse.setMessege(exception.getMessage());
        return ResponseEntity.badRequest().body(appResponse);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<AppResponse<ErrorResponse>> handleValidationErrors(WebExchangeBindException ex) {
        Map<String, String> detailMap = ex.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage(),
                        (first, second) -> first));

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                detailMap);

        AppResponse<ErrorResponse> response = new AppResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid input",
                errorResponse);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    ResponseEntity<AppResponse> handlingAccessDeniedException(AccessDeniedException exception) {
        AppResponse appResponse = new AppResponse<>();
        appResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        appResponse.setMessege(exception.getMessage());
        return ResponseEntity.badRequest().body(appResponse);
    }

}
