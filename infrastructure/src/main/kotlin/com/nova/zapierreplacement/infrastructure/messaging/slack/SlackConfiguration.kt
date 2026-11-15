package com.nova.zapierreplacement.infrastructure.messaging.slack

import com.nova.zapierreplacement.application.ports.SlackMessagePort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Slack adapter selection.
 *
 * - `twinface.slack.enabled` false or unset → [StubSlackMessageAdapter]
 *   (its own `@ConditionalOnProperty`).
 * - any other value → [liveSlackMessagePort], which fails startup
 *   because the live adapter has not shipped.
 *
 * The failure is raised from a [SlackMessagePort] bean rather than a
 * side "guard" bean on purpose: whichever bean asks for the port first
 * hits this factory, so the actionable message is in the cause chain
 * regardless of bean creation order (instead of an opaque "no bean of
 * type SlackMessagePort"). [LiveSlackAdapterUnavailableFailureAnalyzer]
 * then prints it as Description / Action.
 *
 * When the live adapter lands, this factory returns it and the
 * exception + analyzer go away.
 */
@Configuration
@EnableConfigurationProperties(SlackProperties::class)
class SlackConfiguration {

    // No `havingValue`: matches when the property is set to anything
    // other than "false" — the exact complement of the stub's condition,
    // so exactly one SlackMessagePort candidate always exists.
    @Bean
    @ConditionalOnProperty(prefix = "twinface.slack", name = ["enabled"])
    fun liveSlackMessagePort(): SlackMessagePort = throw LiveSlackAdapterUnavailableException()
}

class LiveSlackAdapterUnavailableException : IllegalStateException(
    "twinface.slack.enabled is not false, but the live Slack adapter is not implemented yet. " +
        "Set twinface.slack.enabled=false (env TWINFACE_SLACK_ENABLED=false) to use the stub adapter.",
)
