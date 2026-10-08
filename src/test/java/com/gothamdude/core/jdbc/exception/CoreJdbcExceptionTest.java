package com.gothamdude.core.jdbc.exception;

import org.junit.jupiter.api.Test;

import static com.gothamdude.core.jdbc.exception.CoreJdbcException.ErrorCode;
import static org.assertj.core.api.Assertions.*;

class CoreJdbcExceptionTest {

    @Test
    void shouldCreateWithErrorCodeAndMessage() {
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.QUERY_FAILURE, "query failed");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.QUERY_FAILURE);
        assertThat(ex.getMessage()).isEqualTo("query failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void shouldCreateWithErrorCodeMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.CONNECTION_FAILURE, "conn failed", cause);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONNECTION_FAILURE);
        assertThat(ex.getMessage()).isEqualTo("conn failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void shouldBeRuntimeException() {
        assertThat(new CoreJdbcException(ErrorCode.CONFIGURATION_ERROR, "msg"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldPreserveCauseChain() {
        Exception root = new IllegalArgumentException("root");
        RuntimeException middle = new RuntimeException("middle", root);
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.QUERY_FAILURE, "query", middle);

        assertThat(ex.getCause()).isSameAs(middle);
        assertThat(ex.getCause().getCause()).isSameAs(root);
    }

    @Test
    void shouldSupportConnectionFailureErrorCode() {
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.CONNECTION_FAILURE, "msg");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONNECTION_FAILURE);
    }

    @Test
    void shouldSupportQueryFailureErrorCode() {
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.QUERY_FAILURE, "msg");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.QUERY_FAILURE);
    }

    @Test
    void shouldSupportTransactionFailureErrorCode() {
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.TRANSACTION_FAILURE, "msg");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TRANSACTION_FAILURE);
    }

    @Test
    void shouldSupportMappingFailureErrorCode() {
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.MAPPING_FAILURE, "msg");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MAPPING_FAILURE);
    }

    @Test
    void shouldSupportConfigurationErrorCode() {
        CoreJdbcException ex = new CoreJdbcException(ErrorCode.CONFIGURATION_ERROR, "msg");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFIGURATION_ERROR);
    }

    @Test
    void errorCodeEnumShouldHaveFiveValues() {
        assertThat(ErrorCode.values()).hasSize(5);
    }

    @Test
    void allErrorCodesShouldBeCreatable() {
        for (ErrorCode code : ErrorCode.values()) {
            CoreJdbcException ex = new CoreJdbcException(code, "test message");
            assertThat(ex.getErrorCode()).isEqualTo(code);
            assertThat(ex.getMessage()).isEqualTo("test message");
        }
    }
}