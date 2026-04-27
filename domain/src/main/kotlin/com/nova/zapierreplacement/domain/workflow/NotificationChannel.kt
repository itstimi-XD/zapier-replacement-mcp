package com.nova.zapierreplacement.domain.workflow

/**
 * The categories of outbound channel a routing rule can target.
 *
 * Concrete adapters (Twilio, Gmail, Slack, etc.) are bound at the
 * infrastructure layer; the domain only commits to the *category*.
 */
enum class NotificationChannel {
    SMS,
    EMAIL,
    SLACK,
    WEBHOOK,
}
