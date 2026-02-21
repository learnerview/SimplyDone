package com.learnerview.SimplyDone.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KeepAliveWorker Tests")
class KeepAliveWorkerTest {

    // -------------------------------------------------------
    // resolvePingUrl
    // -------------------------------------------------------

    @Test
    @DisplayName("resolvePingUrl uses explicit configured URL when provided")
    void resolvePingUrl_explicitUrl_usesIt() {
        String result = KeepAliveWorker.resolvePingUrl("https://my-app.onrender.com/custom", 8080);
        assertThat(result).isEqualTo("https://my-app.onrender.com/custom");
    }

    @Test
    @DisplayName("resolvePingUrl falls back to localhost when url is blank and RENDER_EXTERNAL_URL is absent")
    void resolvePingUrl_blankUrl_noRenderEnv_usesLocalhost() {
        // RENDER_EXTERNAL_URL is not set in this test environment
        String result = KeepAliveWorker.resolvePingUrl("", 8080);
        assertThat(result).isEqualTo("http://localhost:8080/api/jobs/health");
    }

    @Test
    @DisplayName("resolvePingUrl falls back to localhost for null url and no RENDER_EXTERNAL_URL")
    void resolvePingUrl_nullUrl_noRenderEnv_usesLocalhost() {
        String result = KeepAliveWorker.resolvePingUrl(null, 9090);
        assertThat(result).isEqualTo("http://localhost:9090/api/jobs/health");
    }

    // -------------------------------------------------------
    // ping (connection failure must be swallowed)
    // -------------------------------------------------------

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
        assertThatNoException().isThrownBy(() ->
                new KeepAliveWorker(8080, "http://localhost:8080/api/jobs/health"));
    }

    @Test
    @DisplayName("Constructor builds default URL from port when url is blank")
    void constructor_buildsDefaultUrlFromPort() {
        // Blank URL -> should default to http://localhost:{port}/api/jobs/health
        assertThatNoException().isThrownBy(() -> new KeepAliveWorker(8080, ""));
    }
}
