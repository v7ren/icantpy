package net.icantpy.api

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcantpyBridgeTest {
    @AfterTest
    fun clear() {
        IcantpyBridge.setPayload(null)
    }

    @Test
    fun outgoingChatIsNoopWithoutPayload() {
        assertFalse(IcantpyBridge.onOutgoingChat(".icantpy"))
    }

    @Test
    fun outgoingChatForwardsToPayload() {
        val payload = FakePayload()
        IcantpyBridge.setPayload(payload)
        assertTrue(IcantpyBridge.onOutgoingChat(".icantpy"))
        assertEquals(".icantpy", payload.outgoing)
        assertFalse(IcantpyBridge.onOutgoingChat("hello"))
    }

    @Test
    fun incomingChatAndLifecycleForward() {
        val payload = FakePayload()
        IcantpyBridge.setPayload(payload)
        IcantpyBridge.onIncomingChat("hi")
        IcantpyBridge.onTick()
        IcantpyBridge.onServerTick()
        IcantpyBridge.onDisconnect()
        IcantpyBridge.openGui()
        IcantpyBridge.onRenderWorld()
        IcantpyBridge.addWaypointAtLook()
        assertEquals(listOf("chat:hi", "tick", "server", "disconnect", "gui", "world", "waypoint"), payload.events)
    }

    @Test
    fun leapMenuIsNoopWithoutPayload() {
        assertFalse(IcantpyBridge.wantsLeapMenu("Spirit Leap"))
        assertFalse(IcantpyBridge.hideOdinLeapMenu())
    }

    @Test
    fun genericEventsUsePassForUnknownIdsAndReachLegacyCallbacks() {
        val payload = FakePayload()
        IcantpyBridge.setPayload(payload)

        assertEquals(
            IcantpyDispatchResult.PASS,
            IcantpyBridge.dispatch(IcantpyRuntimeEvent("future.event")),
        )
        assertEquals(
            IcantpyDispatchResult.PASS,
            IcantpyBridge.dispatch(IcantpyRuntimeEvent("lifecycle.tick")),
        )
        assertTrue(payload.events.contains("tick"))
    }

    @Test
    fun runtimeEventCopiesContextAndRejectsInvalidIds() {
        val source = mutableMapOf<String, Any?>("message" to "hello")
        val event = IcantpyRuntimeEvent("network.chat.incoming", context = source)
        source["message"] = "changed"

        assertEquals("hello", event.context["message"])
        assertEquals(IcantpyDispatchResult.PASS, IcantpyBridge.dispatch(event))
        assertFailsWith<IllegalArgumentException> { IcantpyRuntimeEvent("Invalid ID") }
    }

    @Test
    fun payloadCanHandleAFeatureWithoutANewBridgeMethod() {
        val payload = object : IcantpyPayload {
            override fun onLoad() {}
            override fun onUnload() {}
            override fun dispatch(event: IcantpyRuntimeEvent): IcantpyDispatchResult =
                if (event.id == "feature.future_action") IcantpyDispatchResult.HANDLED
                else IcantpyDispatchResult.PASS
        }
        IcantpyBridge.setPayload(payload)

        assertEquals(
            IcantpyDispatchResult.HANDLED,
            IcantpyBridge.dispatch(IcantpyRuntimeEvent("feature.future_action")),
        )
    }

    private class FakePayload : IcantpyPayload {
        var outgoing: String = ""
        val events = mutableListOf<String>()

        override fun onLoad() {}

        override fun onUnload() {}

        override fun onOutgoingChat(message: String): Boolean {
            outgoing = message
            return message.equals(".icantpy", ignoreCase = true)
        }

        override fun onIncomingChat(message: String) {
            events += "chat:$message"
        }

        override fun onRenderWorld() {
            events += "world"
        }

        override fun onTick() {
            events += "tick"
        }

        override fun onServerTick() {
            events += "server"
        }

        override fun onDisconnect() {
            events += "disconnect"
        }

        override fun openGui() {
            events += "gui"
        }

        override fun addWaypointAtLook() {
            events += "waypoint"
        }
    }
}
