package io.github.huseyinbabal.taskhub.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the pagination guard (SPEC §Session 2 acceptance #4). */
class PageRequestsTest {

    @Test
    void usesRequestedPageAndSizeWhenValid() {
        PageRequest request = PageRequests.of(2, 25);

        assertThat(request.getPageNumber()).isEqualTo(2);
        assertThat(request.getPageSize()).isEqualTo(25);
    }

    @Test
    void capsOversizedSizeToMax() {
        PageRequest request = PageRequests.of(0, 5_000);

        assertThat(request.getPageSize()).isEqualTo(PageRequests.MAX_SIZE);
    }

    @Test
    void fallsBackToDefaultSizeWhenMissingOrNonPositive() {
        assertThat(PageRequests.of(0, null).getPageSize()).isEqualTo(PageRequests.DEFAULT_SIZE);
        assertThat(PageRequests.of(0, 0).getPageSize()).isEqualTo(PageRequests.DEFAULT_SIZE);
        assertThat(PageRequests.of(0, -3).getPageSize()).isEqualTo(PageRequests.DEFAULT_SIZE);
    }

    @Test
    void floorsNegativePageToZero() {
        assertThat(PageRequests.of(-1, 10).getPageNumber()).isZero();
        assertThat(PageRequests.of(null, 10).getPageNumber()).isZero();
    }
}
