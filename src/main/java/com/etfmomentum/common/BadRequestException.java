package com.etfmomentum.common;

/** 业务校验错误(返回 400 + message) */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
