package com.shopsphere.payment_service.controller;

import com.shopsphere.payment_service.service.PaymentService.PaymentNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            PaymentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(
            RuntimeException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", exception instanceof IllegalArgumentException
                        ? exception.getMessage()
                        : "Payment request conflicts with existing data"));
    }
}
