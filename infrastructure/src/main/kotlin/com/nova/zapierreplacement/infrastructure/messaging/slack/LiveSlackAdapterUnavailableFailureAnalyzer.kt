package com.nova.zapierreplacement.infrastructure.messaging.slack

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis

/**
 * Prints [LiveSlackAdapterUnavailableException] as Spring Boot's
 * "APPLICATION FAILED TO START" Description / Action block instead of a
 * nested bean-creation stack trace. Registered in
 * `META-INF/spring.factories`.
 */
class LiveSlackAdapterUnavailableFailureAnalyzer :
    AbstractFailureAnalyzer<LiveSlackAdapterUnavailableException>() {

    override fun analyze(rootFailure: Throwable, cause: LiveSlackAdapterUnavailableException) = FailureAnalysis(
        "twinface.slack.enabled is not false, but the live Slack adapter is not implemented yet. " +
            "The stub adapter is disabled when Slack is enabled so it cannot report success for messages that were never sent.",
        "Set twinface.slack.enabled=false (env TWINFACE_SLACK_ENABLED=false) to run with the stub adapter.",
        cause,
    )
}
