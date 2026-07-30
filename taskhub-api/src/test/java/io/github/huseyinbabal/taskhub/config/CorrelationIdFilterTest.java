package io.github.huseyinbabal.taskhub.config;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.huseyinbabal.taskhub.notification.grpc.CorrelationId;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The correlation id contract from Session 3, plus the access log Session 8
 * needs: without a line emitted inside the request there is nothing in Loki
 * carrying the request's trace id.
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    private ListAppender<ILoggingEvent> logs;

    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        this.filter = new CorrelationIdFilter();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.logger = context.getLogger(CorrelationIdFilter.class);
        this.logs = new ListAppender<>();
        this.logs.start();
        this.logger.addAppender(this.logs);
        this.logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        this.logger.detachAppender(this.logs);
    }

    @Test
    void reusesTheCallersCorrelationIdAndEchoesItBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader(CorrelationId.HEADER, "caller-supplied-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        this.filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("caller-supplied-id");
    }

    @Test
    void generatesACorrelationIdWhenTheCallerSuppliesNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        this.filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    }

    @Test
    void logsOneLinePerRequestCarryingMethodPathAndStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects/1/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        this.filter.doFilter(request, response, mock(FilterChain.class));

        List<ILoggingEvent> events = this.logs.list;
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getFormattedMessage())
                .contains("POST")
                .contains("/api/projects/1/tasks")
                .contains("201");
    }
}
