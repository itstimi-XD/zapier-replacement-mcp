package com.nova.zapierreplacement.domain.workflow

/**
 * A single step in a [SequentialPipeline]. Each step represents one
 * outbound notification (the body is built at the application layer
 * via a callback so domain stays free of templating concerns).
 *
 * Counterpart to [RoutingRule]: where rules describe parallel branches,
 * steps describe sequential stages.
 */
data class PipelineStep(
    val id: String,
    val name: String,
    val targetChannel: NotificationChannel,
)
