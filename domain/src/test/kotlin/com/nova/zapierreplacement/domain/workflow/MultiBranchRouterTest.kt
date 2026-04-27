package com.nova.zapierreplacement.domain.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiBranchRouterTest {

    @Test
    fun `routes event to all matching branches`() {
        val rules = listOf(
            RoutingRule(
                id = "r1",
                branchName = "high-priority-sms",
                targetChannel = NotificationChannel.SMS,
                matcher = { it.payload["priority"] == "high" },
            ),
            RoutingRule(
                id = "r2",
                branchName = "all-events-slack",
                targetChannel = NotificationChannel.SLACK,
                matcher = { true },
            ),
            RoutingRule(
                id = "r3",
                branchName = "billing-email",
                targetChannel = NotificationChannel.EMAIL,
                matcher = { it.type == "billing" },
            ),
        )
        val router = MultiBranchRouter(rules)

        val event = WorkflowEvent(
            id = "evt-1",
            type = "billing",
            payload = mapOf("priority" to "high"),
        )

        val decision = router.route(event)

        assertEquals(3, decision.matchedBranches.size)
        assertEquals(
            listOf("r1", "r2", "r3"),
            decision.matchedBranches.map { it.ruleId },
        )
    }

    @Test
    fun `returns empty branches when no rules match`() {
        val router = MultiBranchRouter(
            listOf(
                RoutingRule(
                    id = "r1",
                    branchName = "never",
                    targetChannel = NotificationChannel.SMS,
                    matcher = { false },
                ),
            ),
        )

        val event = WorkflowEvent(
            id = "evt",
            type = "anything",
            payload = emptyMap(),
        )

        val decision = router.route(event)

        assertTrue(decision.matchedBranches.isEmpty())
    }

    @Test
    fun `preserves rule declaration order in matched branches`() {
        val rules = listOf(
            RoutingRule("z", "z", NotificationChannel.SMS) { true },
            RoutingRule("a", "a", NotificationChannel.SMS) { true },
            RoutingRule("m", "m", NotificationChannel.SMS) { true },
        )
        val router = MultiBranchRouter(rules)

        val event = WorkflowEvent(id = "e", type = "any", payload = emptyMap())

        val decision = router.route(event)

        assertEquals(listOf("z", "a", "m"), decision.matchedBranches.map { it.ruleId })
    }
}
