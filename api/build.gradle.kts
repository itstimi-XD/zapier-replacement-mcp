// :api — Spring Boot entry point + (later) MCP protocol layer via @McpTool.
// This is the only module that knows MCP exists. If MCP standard ever gets replaced,
// only this module changes — domain/application stay intact.

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencyManagement {
    imports {
        // Spring AI BOM aligns the MCP server starter with the rest of
        // the Spring AI artifact set. Pinned to a milestone version
        // (1.0.0-M6) — APIs may shift before GA; that risk lives only
        // in :api by the project's MCP-isolation rule.
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0-M6")
    }
}

dependencies {
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Spring AI MCP server (Web MVC transport). Exposes any bean's
    // `@Tool`-annotated methods over the MCP protocol. The starter
    // artifact name is the legacy `*-spring-boot-starter` form for
    // 1.0.0-M6; the BOM still pins the version.
    implementation("org.springframework.ai:spring-ai-mcp-server-webmvc-spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
