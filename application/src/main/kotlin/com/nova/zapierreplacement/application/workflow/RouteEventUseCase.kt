package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.MatchedBranch
import com.nova.zapierreplacement.domain.workflow.MultiBranchRouter
import com.nova.zapierreplacement.domain.workflow.RoutingDecision
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/**
 * Orchestrates [MultiBranchRouter] decisions into actual side effects.
 *
 * Pure routing → fan-out dispatch in parallel via coroutines. Each matched
 * branch is independent; failures in one branch don't cancel siblings.
 * Caller decides retry / DLQ semantics by inspecting the per-branch results.
 *
 * Implementation notes:
 * - We use [supervisorScope] specifically so that an uncaught exception in one
 *   `async` does NOT cancel the others. Inside that scope, the per-branch
 *   `try/catch` converts adapter failures into [DispatchResult.Failed] while
 *   re-throwing [CancellationException] to keep structured concurrency intact
 *   (cancellation must propagate up so callers can implement timeouts cleanly).
 *
 * The [messageBuilder] callback lets callers customize the body shape per
 * branch without leaking adapter concerns into the use case itself.
 */
class RouteEventUseCase(
    private val router: MultiBranchRouter,
    private val dispatcher: MessageDispatchPort,
    private val messageBuilder: (MatchedBranch, WorkflowEvent) -> DispatchMessage,
) {

    suspend fun execute(event: WorkflowEvent): RouteEventResult = supervisorScope {
        val decision = router.route(event)

        val branchResults = decision.matchedBranches
            .map { branch ->
                async {
                    val message = messageBuilder(branch, event)
                    val result = try {
                        dispatcher.dispatch(branch.targetChannel, message)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (e: Exception) {
                        // Catch Exception, not Throwable — Errors (OOM, StackOverflow,
                        // LinkageError) must propagate so the JVM can surface them.
                        DispatchResult.Failed(reason = e.message ?: "unknown")
                    }
                    branch to result
                }
            }
            .map { it.await() }

        RouteEventResult(decision = decision, branchResults = branchResults)
    }
}

data class RouteEventResult(
    val decision: RoutingDecision,
    val branchResults: List<Pair<MatchedBranch, DispatchResult>>,
)
