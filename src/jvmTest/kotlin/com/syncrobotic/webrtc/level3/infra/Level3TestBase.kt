package com.syncrobotic.webrtc.level3.infra

import org.junit.Assume
import java.io.File

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
        /**
         * Check if Docker is available for running containers.
         * Tests will be skipped if Docker is not accessible.
         */
        fun isDockerAvailable(): Boolean {
            return try {
                val process = ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                exitCode == 0
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
