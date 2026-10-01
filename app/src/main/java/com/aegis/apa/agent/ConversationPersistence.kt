package com.aegis.apa.agent

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class ConversationMessageState : MutableState<List<AgentConversationMessage>> {
    private val state = mutableStateOf<List<AgentConversationMessage>>(emptyList())
    var onChanged: ((List<AgentConversationMessage>) -> Unit)? = null
    override var value: List<AgentConversationMessage>
        get() = state.value
        set(value) {
            val bounded = value.takeLast(100)
            if (state.value == bounded) return
            state.value = bounded
            onChanged?.invoke(state.value)
        }
    override fun component1() = value
    override fun component2(): (List<AgentConversationMessage>) -> Unit = { value = it }
}

/** Application-owned actor: writes remain ordered and survive Activity/ViewModel disposal. */
class ConversationPersistence(
    private val store: ConversationStore,
    private val completionDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private sealed interface Command {
        data class Save(val messages: List<AgentConversationMessage>, val completed: (Boolean) -> Unit) : Command
        data class Load(val result: CompletableDeferred<ConversationLoadResult>) : Command
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Save -> {
                        val success = runCatching { store.save(command.messages) }.isSuccess
                        withContext(completionDispatcher) { command.completed(success) }
                    }
                    is Command.Load -> command.result.complete(store.load())
                }
            }
        }
    }

    suspend fun load(): ConversationLoadResult {
        val result = CompletableDeferred<ConversationLoadResult>()
        commands.send(Command.Load(result))
        return result.await()
    }
    fun save(messages: List<AgentConversationMessage>, completed: (Boolean) -> Unit) {
        commands.trySend(Command.Save(messages.toList(), completed))
    }
}
