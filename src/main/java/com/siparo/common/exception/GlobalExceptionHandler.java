package com.siparo.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tek hata sözleşmesi. İstemci yalnızca {@code code} alanına göre metin üretir; {@code message} sunucu tarafı açıklamadır.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorResponse body = body(ex.getStatus(), ex.getCode() != null ? ex.getCode() : "BUSINESS_RULE", ex.getMessage());
        body.setParams(ex.getParams());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), validationCode(error.getCode()));
        }
        ErrorResponse body = body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed");
        body.setFieldErrors(fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request is missing or contains an invalid value"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String code = status.value() == 404 ? "NOT_FOUND" : "HTTP_" + status.value();
        return ResponseEntity.status(status).body(body(status, code, ex.getReason()));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> handleConflict(Exception ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT, "CONFLICT", "The resource was modified concurrently"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File is too large"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled API exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ErrorResponse body(HttpStatusCode status, String code, String message) {
        String reason = status instanceof HttpStatus known ? known.getReasonPhrase() : String.valueOf(status.value());
        ErrorResponse response = new ErrorResponse(status.value(), reason, message, LocalDateTime.now());
        response.setCode(code);
        return response;
    }

    private String validationCode(String constraint) {
        if (constraint == null) return "INVALID";
        return switch (constraint) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Email" -> "INVALID_EMAIL";
            case "Size", "Length" -> "INVALID_LENGTH";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "Range" -> "OUT_OF_RANGE";
            default -> "INVALID";
        };
    }
}
