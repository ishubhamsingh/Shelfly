package dev.ishubhamsingh.shelfly.agent

import org.json.JSONObject

sealed interface AgentTurn {
    data class ToolCall(
        val functionName: String,
        val args: JSONObject,
        val result: JSONObject,
        val reply: String,
    ) : AgentTurn

    /** LLM chose an add/destructive tool but we haven't executed it yet — needs user confirmation. */
    data class Pending(
        val functionName: String,
        val args: JSONObject,
    ) : AgentTurn

    data class Reply(val text: String) : AgentTurn

    data class Failure(val reason: String) : AgentTurn
}
