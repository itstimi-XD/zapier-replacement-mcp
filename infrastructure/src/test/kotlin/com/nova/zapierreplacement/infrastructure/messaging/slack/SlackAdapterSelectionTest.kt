package com.nova.zapierreplacement.infrastructure.messaging.slack

import com.nova.zapierreplacement.application.ports.SlackMessagePort
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `twinface.slack.enabled` decides which [SlackMessagePort] exists.
 * The stub must never answer when the operator asked for real Slack:
 * a stub that reports success while nothing is sent is worse than a
 * startup failure.
 */
class SlackAdapterSelectionTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(SlackConfiguration::class.java, StubSlackMessageAdapter::class.java)

    @Test
    fun `stub adapter is the port when enabled is missing`() {
        runner.run { context ->
            assertNull(context.startupFailure)
            assertIs<StubSlackMessageAdapter>(context.getBean(SlackMessagePort::class.java))
        }
    }

    // Runs the stub alone (no SlackConfiguration) so the context can start and
    // the stub's own @ConditionalOnProperty is what is under test. Without this,
    // removing that condition would go unnoticed: the enabled=true case fails
    // either way because the live factory throws.
    @Test
    fun `stub adapter steps aside when enabled is true`() {
        ApplicationContextRunner()
            .withUserConfiguration(StubSlackMessageAdapter::class.java)
            .withPropertyValues("twinface.slack.enabled=true")
            .run { context ->
                assertNull(context.startupFailure)
                assertTrue(context.getBeanNamesForType(StubSlackMessageAdapter::class.java).isEmpty())
            }
    }

    @Test
    fun `stub adapter is the port when enabled is false`() {
        runner.withPropertyValues("twinface.slack.enabled=false").run { context ->
            assertNull(context.startupFailure)
            assertIs<StubSlackMessageAdapter>(context.getBean(SlackMessagePort::class.java))
        }
    }

    @Test
    fun `startup fails with an actionable message when enabled is true`() {
        runner.withPropertyValues("twinface.slack.enabled=true").run { context ->
            val failure = assertNotNull(context.startupFailure)
            val cause = generateSequence(failure as Throwable) { it.cause }
                .filterIsInstance<LiveSlackAdapterUnavailableException>()
                .firstOrNull()
            val message = assertNotNull(cause, "expected LiveSlackAdapterUnavailableException in $failure").message!!
            assertContains(message, "live Slack adapter is not implemented yet")
            assertContains(message, "twinface.slack.enabled=false")
        }
    }

    @Test
    fun `failure analyzer turns the exception into a description and an action`() {
        val analysis = assertNotNull(
            LiveSlackAdapterUnavailableFailureAnalyzer().analyze(
                RuntimeException("wrapper", LiveSlackAdapterUnavailableException()),
            ),
        )
        assertContains(analysis.description, "twinface.slack.enabled is not false")
        assertContains(analysis.action, "twinface.slack.enabled=false")
    }

    @Test
    fun `token binds from properties and never appears in toString`() {
        runner.withPropertyValues("twinface.slack.token=secret-bot-token-123").run { context ->
            val props = context.getBean(SlackProperties::class.java)
            assertTrue(props.token == "secret-bot-token-123")
            assertFalse(props.toString().contains("secret-bot-token-123"))
        }
    }
}
