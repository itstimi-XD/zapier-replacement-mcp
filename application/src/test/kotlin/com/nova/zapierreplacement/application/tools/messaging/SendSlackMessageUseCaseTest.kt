package com.nova.zapierreplacement.application.tools.messaging

import com.nova.zapierreplacement.application.ports.SlackMessagePort
import com.nova.zapierreplacement.application.ports.SlackSendResult
import com.nova.zapierreplacement.application.ports.WorkspaceContextPort
import com.nova.zapierreplacement.domain.workspace.WorkspaceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SendSlackMessageUseCaseTest {

    private val workspace = WorkspaceId("ws-test")

    @Test
    fun `dispatches to slack with the workspace resolved from context`() = runTest {
        val context = StaticContext(workspace)
        val slack = RecordingSlack()

        val result = SendSlackMessageUseCase(context, slack).execute(
            channel = "#general",
            text = "hello world",
        )

        val sent = assertIs<SlackSendResult.Sent>(result)
        assertEquals("#general", sent.channel)
        // The adapter received the workspace that the context resolved,
        // not anything passed by the caller — that's the "implicit
        // workspace_id" invariant the pattern exists for.
        assertEquals(workspace, slack.last?.workspace)
    }

    @Test
    fun `passes threadTs through when provided`() = runTest {
        val slack = RecordingSlack()

        SendSlackMessageUseCase(StaticContext(workspace), slack).execute(
            channel = "#general",
            text = "reply",
            threadTs = "1234.5",
        )

        assertEquals("1234.5", slack.last?.threadTs)
    }

    @Test
    fun `surfaces adapter Failed result without translating it into an exception`() = runTest {
        val slack = AlwaysFailingSlack(reason = "channel_not_found")

        val result = SendSlackMessageUseCase(StaticContext(workspace), slack).execute(
            channel = "#does-not-exist",
            text = "hello",
        )

        val failed = assertIs<SlackSendResult.Failed>(result)
        assertEquals("channel_not_found", failed.reason)
    }

    @Test
    fun `rejects blank channel before reaching the adapter`() = runTest {
        val slack = RecordingSlack()

        assertFailsWith<IllegalArgumentException> {
            SendSlackMessageUseCase(StaticContext(workspace), slack).execute(
                channel = "",
                text = "hello",
            )
        }
        // The whole point of validating in the use case: the adapter
        // should never receive an obviously-bad call.
        assertEquals(null, slack.last)
    }

    @Test
    fun `rejects whitespace-only channel`() = runTest {
        // The use case uses isNotBlank() rather than isEmpty(). Pinning
        // the whitespace case here so a refactor to isEmpty() doesn't
        // silently regress to "  " being accepted as a channel name.
        val slack = RecordingSlack()

        assertFailsWith<IllegalArgumentException> {
            SendSlackMessageUseCase(StaticContext(workspace), slack).execute(
                channel = "   ",
                text = "hello",
            )
        }
        assertEquals(null, slack.last)
    }

    @Test
    fun `accepts text at exactly the 40,000 character upper bound`() = runTest {
        // The `in 1..40_000` range is inclusive at both ends; this test
        // pins the upper boundary so an off-by-one to `< 40_000` would
        // surface here instead of in production.
        val slack = RecordingSlack()

        val result = SendSlackMessageUseCase(StaticContext(workspace), slack).execute(
            channel = "#general",
            text = "x".repeat(40_000),
        )

        assertIs<SlackSendResult.Sent>(result)
        assertEquals(40_000, slack.last?.text?.length)
    }

    @Test
    fun `rejects empty text`() = runTest {
        val slack = RecordingSlack()

        assertFailsWith<IllegalArgumentException> {
            SendSlackMessageUseCase(StaticContext(workspace), slack).execute(
                channel = "#general",
                text = "",
            )
        }
        assertEquals(null, slack.last)
    }

    @Test
    fun `rejects text exceeding Slack's 40k character limit`() = runTest {
        val slack = RecordingSlack()

        assertFailsWith<IllegalArgumentException> {
            SendSlackMessageUseCase(StaticContext(workspace), slack).execute(
                channel = "#general",
                text = "x".repeat(40_001),
            )
        }
        assertEquals(null, slack.last)
    }

    private data class SlackCall(
        val workspace: WorkspaceId,
        val channel: String,
        val text: String,
        val threadTs: String?,
    )

    private class StaticContext(private val workspace: WorkspaceId) : WorkspaceContextPort {
        override fun current(): WorkspaceId = workspace
    }

    private class RecordingSlack : SlackMessagePort {
        var last: SlackCall? = null
            private set

        override suspend fun postMessage(
            workspace: WorkspaceId,
            channel: String,
            text: String,
            threadTs: String?,
        ): SlackSendResult {
            last = SlackCall(workspace, channel, text, threadTs)
            return SlackSendResult.Sent(channel = channel, messageTs = "fake-ts")
        }
    }

    private class AlwaysFailingSlack(private val reason: String) : SlackMessagePort {
        override suspend fun postMessage(
            workspace: WorkspaceId,
            channel: String,
            text: String,
            threadTs: String?,
        ): SlackSendResult = SlackSendResult.Failed(reason)
    }
}
