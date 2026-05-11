plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.nova.zapierreplacement"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        // Spring AI 1.0.0-M6 is a milestone release; milestones are
        // published to Spring's milestone repo, not Maven Central.
        // Drop this once Spring AI ships a GA artifact to Central.
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}
