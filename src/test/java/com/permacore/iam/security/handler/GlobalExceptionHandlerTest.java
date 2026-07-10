package com.permacore.iam.security.handler;

import com.permacore.iam.domain.vo.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessErrorsToRealHttpStatuses() {
        assertThat(handler.handleBusinessException(new BusinessException("参数错误")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleBusinessException(
                new BusinessException(ResultCode.UNAUTHORIZED, "登录过期")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleBusinessException(
                new BusinessException(ResultCode.ERROR, "系统错误")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void mapsUnreadableOrInvalidRequestParametersToBadRequest() {
        assertThat(handler.handleBadRequest(new IllegalArgumentException("invalid")).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }
}
