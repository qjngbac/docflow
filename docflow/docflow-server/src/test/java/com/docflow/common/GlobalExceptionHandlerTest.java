package com.docflow.common;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController static class FailureEndpoint {
        @GetMapping("/failure") public void fail() { throw new IllegalStateException("private server details"); }
        @GetMapping("/denied") public void denied() { throw new BusinessException(ErrorCode.FORBIDDEN); }
    }

    @Test void httpStatusAndBusinessCodeAgreeWithoutExposingInternalException() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureEndpoint())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/failure")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getMessage()));
        mvc.perform(get("/denied")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
}
