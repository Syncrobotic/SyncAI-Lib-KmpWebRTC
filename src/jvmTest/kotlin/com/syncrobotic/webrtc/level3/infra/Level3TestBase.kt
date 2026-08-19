package com.syncrobotic.webrtc.level3.infra

import org.junit.Assume
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Base class for Level 3 integration tests.
 *
 * Provides:
 * - Docker availability check
 * - Common infrastructure setup/teardown helpers
 * - Convenience methods for creating test sessions
 */
open class Level3TestBase {

    companion object {
        /** Bound on the `docker info` availability probe. */
        private const val DOCKER_PROBE_TIMEOUT_SECONDS = 5L

        /**
         * Check if Docker is available for running containers.
         * Tests will be skipped if Docker is not accessible.
         *
         * A hung Docker daemon accepts the socket but never answers, so `docker info`
         * blocks forever. Without a bounded wait this probe — whose whole job is to let
         * the test skip — becomes the thing that hangs the entire jvmTest run, and with
         * it the pre-push hook. Treat "no answer in time" as "not available".
         */
        fun isDockerAvailable(): Boolean {
            return try {
                val process = ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start()
                if (!process.waitFor(DOCKER_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Check if Colima is running (macOS alternative to Docker Desktop).
         */
        fun isColimaRunning(): Boolean {
            val colimaSocket = File("${System.getProperty("user.home")}/.colima/default/docker.sock")
            return colimaSocket.exists()
        }

        /**
         * Skip test if Docker is not available.
         */
        fun assumeDockerAvailable() {
            Assume.assumeTrue(
                "Docker is not available — skipping Level 3 test",
                isDockerAvailable()
            )
        }
    }
}
