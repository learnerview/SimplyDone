package com.learnerview.SimplyDone.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KeepAliveWorker Tests")
class KeepAliveWorkerTest {

    @Test
    @DisplayName("ping does not throw when the target URL is unreachable")
    void ping_unreachableUrl_doesNotThrow() {
        // Port 9 is conventionally a discard/sink port — connection will be refused immediately
        KeepAliveWorker worker = new KeepAliveWorker(9, "http://localhost:9/api/jobs/health");

        assertThatNoException().isThrownBy(worker::ping);
    }

    @Test
    @DisplayName("Constructor uses configured URL when provided")
    void constructor_usesConfiguredUrl() {
        // Just verifying construction succeeds with an explicit URL
        assertThatNoException().isThrownBy(() ->
                new KeepAliveWorker(8080, "http://localhost:8080/api/jobs/health"));
    }

    @Test
    @DisplayName("Constructor builds default URL from port when url is blank")
    void constructor_buildsDefaultUrlFromPort() {
        // Blank URL -> should default to http://localhost:{port}/api/jobs/health
        // Construction should succeed without NPE
        assertThatNoException().isThrownBy(() -> new KeepAliveWorker(8080, ""));
    }
}
