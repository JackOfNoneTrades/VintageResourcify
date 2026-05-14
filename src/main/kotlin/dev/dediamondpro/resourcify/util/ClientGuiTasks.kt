package dev.dediamondpro.resourcify.util

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import dev.dediamondpro.resourcify.VintageResourcify
import java.util.concurrent.ConcurrentLinkedQueue

object ClientGuiTasks {

    private val queue = ConcurrentLinkedQueue<Runnable>()

    fun runNextClientTick(action: () -> Unit) {
        queue.add(Runnable(action))
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        val count = queue.size
        repeat(count) {
            val action = queue.poll() ?: return
            try {
                action.run()
            } catch (t: Throwable) {
                VintageResourcify.LOG.error("Deferred client GUI task failed", t)
            }
        }
    }
}
