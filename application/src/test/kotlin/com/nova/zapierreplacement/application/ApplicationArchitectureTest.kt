package com.nova.zapierreplacement.application

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Architecture rules for the application layer.
 *
 * `:application` is the layer most likely to be corrupted by a future
 * "just import Spring's @Service real quick" mistake. These tests close
 * that gap so a wrong import fails the build, not just code review.
 *
 * If any of these tests fail, do NOT add suppressions. Move the
 * offending class to `:infrastructure` (where Spring belongs) or
 * redesign the use case so the dependency goes through a port.
 */
class ApplicationArchitectureTest {

    private val applicationClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.nova.zapierreplacement.application")

    @Test
    fun `application must not depend on Spring Framework`() {
        noClasses()
            .that().resideInAPackage("com.nova.zapierreplacement.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
            )
            .check(applicationClasses)
    }

    @Test
    fun `application must not depend on infrastructure or api modules`() {
        noClasses()
            .that().resideInAPackage("com.nova.zapierreplacement.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.nova.zapierreplacement.infrastructure..",
                "com.nova.zapierreplacement.api..",
            )
            .check(applicationClasses)
    }

    @Test
    fun `application must not depend on persistence or serialization libraries`() {
        noClasses()
            .that().resideInAPackage("com.nova.zapierreplacement.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "javax.persistence..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson..",
            )
            .check(applicationClasses)
    }
}
