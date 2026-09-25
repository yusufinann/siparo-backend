package com.siparo.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * İş kuralı ihlali. {@code code} istemci sözleşmesidir: istemci metni koddan (i18n) üretir, {@code message} yalnızca log/geliştirici içindir.
 * {@code params} koda eşlik eden değerleri taşır (örn. minimum tutar).
 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> params;

    public BusinessException(String message) {
        this(null, message, HttpStatus.BAD_REQUEST, null);
    }

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST, null);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public BusinessException(String code, String message, Map<String, Object> params) {
        this(code, message, HttpStatus.BAD_REQUEST, params);
    }

    public BusinessException(String code, String message, HttpStatus status, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.status = status;
        this.params = params;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
