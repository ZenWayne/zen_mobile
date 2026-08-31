package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalGateTest {
    private class Signal(var flag: Boolean = false) : CancellationSignal {
        override val cancelled get() = flag
    }

    @Test
    fun approveResolvesAwait() {
        val gate = ApprovalGate()
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<Decision> { gate.await(Signal()) }
        Thread.sleep(50)
        gate.approve()
        assertEquals(Decision.Approved, fut.get(2, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun denyResolvesAwait() {
        val gate = ApprovalGate()
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<Decision> { gate.await(Signal()) }
        Thread.sleep(50)
        gate.deny()
        assertEquals(Decision.Denied, fut.get(2, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun cancelledRunYieldsDenied() {
        val gate = ApprovalGate()
        val sig = Signal(flag = true)
        assertEquals(Decision.Denied, gate.await(sig, pollMillis = 10))
    }
}
