package com.gothamdude.core.jdbc.exception;

import lombok.Getter;

@Getter
public class CoreJdbcException extends RuntimeException {

    private final ErrorCode errorCode;

    public enum ErrorCode {
        CONNECTION_FAILURE,
        CONFIGURATION_ERROR,
        MAPPING_FAILURE,
        QUERY_FAILURE,
        TRANSACTION_FAILURE
    }

    public CoreJdbcException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CoreJdbcException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
