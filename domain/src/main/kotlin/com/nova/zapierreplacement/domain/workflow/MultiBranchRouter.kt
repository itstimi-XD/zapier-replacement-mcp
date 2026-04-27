package com.nova.zapierreplacement.domain.workflow

/**
 * Pure routing logic for the multi-branch parallel-paths anti-pattern.
 *
 * Given a list of [RoutingRule]s, this class takes a single [WorkflowEvent]
 * and returns the set of branches that should fire. No I/O, no coroutines —
 * the orchestration of "actually dispatching" is the application layer's job.
 *
 * The rule list is evaluated in declaration order; multiple rules can match.
 * If you need short-circuiting "first match wins", that is a different policy
 * and should be a separate router type — keep this one focused.
 */
class MultiBranchRouter(private val rules: List<RoutingRule>) {

    fun route(event: WorkflowEvent): RoutingDecision {
        val matched = rules
            .filter { it.matcher(event) }
            .map {
                MatchedBranch(
                    ruleId = it.id,
                    branchName = it.branchName,
                    targetChannel = it.targetChannel,
                )
            }

        return RoutingDecision(event = event, matchedBranches = matched)
    }
}
