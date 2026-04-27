package com.nova.zapierreplacement.domain.workflow

/**
 * A single rule that decides whether an incoming [WorkflowEvent]
 * should fan out to a particular branch.
 *
 * The whole point of the multi-branch-router pattern: in Zapier,
 * every Path step burns a task even when the Path doesn't match.
 * Here, branches are filters over a single in-memory predicate —
 * non-matching branches are free.
 */
data class RoutingRule(
    val id: String,
    val branchName: String,
    val targetChannel: NotificationChannel,
    val matcher: (WorkflowEvent) -> Boolean,
)
