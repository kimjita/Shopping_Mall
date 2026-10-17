package com.sprint.mission.global.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 필터처럼 컨트롤러 밖에서 난 에러는 컨테이너가 /error로 보낸다. Boot 기본 응답 대신 공통 {@link ErrorResponse}를 쓴다.
 * Accept 헤더와 상관없이 항상 JSON이 나가도록 {@link ErrorResponseWriter}로 응답에 직접 쓴다.
 */
@RestController
public class GlobalErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorController.class);

    private final ErrorResponseWriter errorResponseWriter;

    public GlobalErrorController(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @RequestMapping("${server.error.path:/error}")
    void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatus status = resolveStatus(request);
        if (status.is5xxServerError()) {
            log.error("컨트롤러 밖에서 처리되지 않은 예외", (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        }
        errorResponseWriter.write(response, ErrorCode.from(status));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
