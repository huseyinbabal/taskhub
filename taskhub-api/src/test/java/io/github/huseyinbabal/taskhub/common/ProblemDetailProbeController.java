package io.github.huseyinbabal.taskhub.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller that deliberately throws, so {@link GlobalExceptionHandler}
 * can be exercised as a slice (SPEC §5 error contract). Top-level (not nested in
 * the test) so {@code @WebMvcTest(controllers = ...)} registers its mappings.
 */
@RestController
class ProblemDetailProbeController {

    @GetMapping("/__test/not-found")
    String notFound() {
        throw new ResourceNotFoundException("Task 42 not found");
    }

    @GetMapping("/__test/boom")
    String boom() {
        throw new IllegalStateException("internal detail that must not leak");
    }
}
