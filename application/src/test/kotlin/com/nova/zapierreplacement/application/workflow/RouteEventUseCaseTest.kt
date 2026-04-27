package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.MultiBranchRouter
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.RoutingRule
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RouteEventUseCaseTest {

    private val event = WorkflowEvent(id = "e1", type = "any", payload = emptyMap())

    @Test
    fun `dispatches once per matched branch with the right channel`() = runTest {
        val rules = listOf(
            RoutingRule("r1", "branch-sms", NotificationChannel.SMS) { true },
            RoutingRule("r2", "branch-email", NotificationChannel.EMAIL) { true },
        )
        val dispatcher = RecordingDispatcher()

        val result = RouteEventUseCase(
            router = MultiBranchRouter(rules),
            dispatcher = dispatcher,
            messageBuilder = { branch, evt -> DispatchMessage(branch.branchName, evt.id) },
        ).execute(event)

        assertEquals(2, result.branchResults.size)
        assertEquals(
            listOf(
                NotificationChannel.SMS to "branch-sms",
                NotificationChannel.EMAIL to "branch-email",
            ),
            dispatcher.calls.map { it.channel to it.message.recipient },
        )
    }

    @Test
    fun `failure in one branch does not cancel siblings`() = runTest {
        val rules = listOf(
            RoutingRule("r1", "ok", NotificationChannel.SMS) { true },
            RoutingRule("r2", "fail", NotificationChannel.SMS) { true },
            RoutingRule("r3", "also-ok", NotificationChannel.SMS) { true },
        )
        val dispatcher = SelectiveFailingDispatcher(failingRecipient = "fail")

        val result = RouteEventUseCase(
            router = MultiBranchRouter(rules),
            dispatcher = dispatcher,
            messageBuilder = { branch, evt -> DispatchMessage(branch.branchName, evt.id) },
        ).execute(event)

        val byBranch = result.branchResults.associate { it.first.branchName to it.second }
        assertIs<DispatchResult.Sent>(byBranch["ok"])
        assertIs<DispatchResult.Failed>(byBranch["fail"])
        assertIs<DispatchResult.Sent>(byBranch["also-ok"])
    }

    @Test
    fun `no matched branches produces empty results and never touches dispatcher`() = runTest {
        val rules = listOf(
            RoutingRule("never", "never", NotificationChannel.SMS) { false },
        )
        val dispatcher = RecordingDispatcher()

        val result = RouteEventUseCase(
            router = MultiBranchRouter(rules),
            dispatcher = dispatcher,
            messageBuilder = { branch, evt -> DispatchMessage(branch.branchName, evt.id) },
        ).execute(event)

        assertTrue(result.branchResults.isEmpty())
        assertEquals(0, dispatcher.calls.size)
    }

    private data class DispatchCall(val channel: NotificationChannel, val message: DispatchMessage)

    private class RecordingDispatcher : MessageDispatchPort {
        val calls = mutableListOf<DispatchCall>()
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            calls += DispatchCall(channel, message)
            return DispatchResult.Sent(externalId = "ext-${calls.size}")
        }
    }

    private class SelectiveFailingDispatcher(
        private val failingRecipient: String,
    ) : MessageDispatchPort {
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            if (message.recipient == failingRecipient) {
                throw RuntimeException("simulated adapter failure")
            }
            return DispatchResult.Sent(externalId = "ok-${message.recipient}")
        }
    }
}
