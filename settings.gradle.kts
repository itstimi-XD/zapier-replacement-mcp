plugins {
    // Auto-provisions the Java 21 toolchain on first build (no manual JDK install needed).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "zapier-replacement-mcp"

include(
    ":domain",
    ":application",
    ":infrastructure",
    ":api",
)
