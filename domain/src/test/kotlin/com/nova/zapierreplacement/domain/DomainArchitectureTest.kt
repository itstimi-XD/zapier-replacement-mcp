package com.nova.zapierreplacement.domain

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * The domain module is the architectural fortress. These rules are the
 * second line of defense after Gradle module boundaries — they catch
 * accidental imports that would technically compile (e.g. JDK or stdlib
 * abuses that smuggle infrastructure concerns into pure logic).
 *
 * If any of these tests fail, do NOT add suppressions. Fix the import.
 */
class DomainArchitectureTest {

    private val domainClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.nova.zapierreplacement.domain")

    @Test
    fun `domain must not depend on Spring Framework`() {
        noClasses()
            .that().resideInAPackage("com.nova.zapierreplacement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
            )
            .check(domainClasses)
    }

    @Test
    fun `domain must not depend on infrastructure or api modules`() {
        noClasses()
            .that().resideInAPackage("com.nova.zapierreplacement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.nova.zapierreplacement.infrastructure..",
                "com.nova.zapierreplacement.api..",
            )
            .check(domainClasses)
    }

    @Test
    fun `domain must not depend on persistence libraries`() {
        noClasses()
            .that().resideInAPackage("com.nova.zapierreplacement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "javax.persistence..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson..",
            )
            .check(domainClasses)
    }
}
