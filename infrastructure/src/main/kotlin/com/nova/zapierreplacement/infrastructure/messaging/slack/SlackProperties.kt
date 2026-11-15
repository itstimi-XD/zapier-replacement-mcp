package com.nova.zapierreplacement.infrastructure.messaging.slack

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `twinface.slack.*` settings. Bound now so the env vars documented in
 * QUICKSTART (`TWINFACE_SLACK_ENABLED`, `TWINFACE_SLACK_TOKEN`) are
 * accepted today; the live adapter is their first reader.
 *
 * Bound as a plain `@ConfigurationProperties` class (no `@Configuration`)
 * per Spring Boot's recommended style since 2.2; activated via
 * [SlackConfiguration].
 */
@ConfigurationProperties(prefix = "twinface.slack")
class SlackProperties {
    var enabled: Boolean = false
    var baseUrl: String = "https://slack.com/api"

    /**
     * Bot token for the live adapter. Reserved: nothing reads it until
     * that adapter ships. Must never be logged — [toString] masks it.
     */
    var token: String? = null

    override fun toString(): String =
        "SlackProperties(enabled=$enabled, baseUrl=$baseUrl, token=${if (token.isNullOrEmpty()) "<unset>" else "<redacted>"})"
}
