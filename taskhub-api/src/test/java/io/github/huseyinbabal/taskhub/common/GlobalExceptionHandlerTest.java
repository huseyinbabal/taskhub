package io.github.huseyinbabal.taskhub.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the global error contract (SPEC §5, Session 1 acceptance #3):
 * errors surface as RFC-7807 problem-JSON, never a stack trace.
 */
@WebMvcTest
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void resourceNotFound_returnsProblemJson() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.detail").value("Task 42 not found"));
    }

    @Test
    void unhandledException_returnsGenericProblemJson() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                // never leak the raw exception message
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/__test/not-found")
        String notFound() {
            throw new ResourceNotFoundException("Task 42 not found");
        }

        @GetMapping("/__test/boom")
        String boom() {
            throw new IllegalStateException("internal detail that must not leak");
        }
    }
}
