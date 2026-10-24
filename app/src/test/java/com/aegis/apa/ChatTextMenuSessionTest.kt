package com.aegis.apa

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatTextMenuSessionTest {
    @Test fun cancelClosesOnlyTheMenuAndAllowsAnotherLongPress() = runBlocking {
        val menu = ChatTextMenuSession()
        assertFalse(menu.dismiss())
        val closed = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            menu.show { try { awaitCancellation() } finally { closed.complete(Unit) } }
        }
        assertTrue(menu.dismiss())
        assertFalse(menu.dismiss())
        first.join()
        assertTrue(closed.isCompleted)
        assertTrue(coroutineContext[kotlinx.coroutines.Job]!!.isActive)
        val second = launch(start = CoroutineStart.UNDISPATCHED) { menu.show { awaitCancellation() } }
        assertTrue(menu.dismiss())
        second.join()
    }

    @Test fun nativeCompletionAndPageDisposalReleaseTheSession() = runBlocking {
        val menu = ChatTextMenuSession()
        menu.show { }
        assertFalse(menu.dismiss())
        val closed = CompletableDeferred<Unit>()
        val active = launch(start = CoroutineStart.UNDISPATCHED) {
            menu.show { try { awaitCancellation() } finally { closed.complete(Unit) } }
        }
        active.cancel()
        active.join()
        assertTrue(closed.isCompleted)
        assertFalse(menu.dismiss())
    }
}
