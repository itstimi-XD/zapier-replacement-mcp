package com.nova.zapierreplacement.domain.workflow

/**
 * The result of running an event through the router: the original event
 * and the list of branches that matched. Side-effect-free.
 *
 * Empty [matchedBranches] is a valid outcome — events that match nothing
 * simply produce no work, which is the correct semantics.
 */
data class RoutingDecision(
    val event: WorkflowEvent,
    val matchedBranches: List<MatchedBranch>,
)

data class MatchedBranch(
    val ruleId: String,
    val branchName: String,
    val targetChannel: NotificationChannel,
)
