package rkdgus.Event

import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import rkdgus.Info.Info
import rkdgus.Listener.craftBukkit

@Info(description = "블레이즈 막대를 들고 좌클릭하면 바라보는 방향으로 번개를 내립니다.")
class Test : craftBukkit() {
    @EventHandler
    fun onTest(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_AIR && event.action != Action.LEFT_CLICK_BLOCK) {
            return
        }
        if (!isHoldingBlazeRod(event.player, event.hand)) {
            return
        }
        strikeTarget(event.player)
    }

    private fun isHoldingBlazeRod(player: Player, hand: EquipmentSlot?): Boolean {
        if (hand == null) {
            return false
        }
        val item = if (hand == EquipmentSlot.HAND) {
            player.inventory.itemInMainHand
        } else {
            player.inventory.itemInOffHand
        }
        return item.type == Material.BLAZE_ROD
    }

    private fun strikeTarget(player: Player) {
        val eyeLocation = player.eyeLocation
        val direction = eyeLocation.direction
        val hit = player.world.rayTrace(
            eyeLocation,
            direction,
            100.0,
            org.bukkit.FluidCollisionMode.NEVER,
            true,
            0.3
        ) { entity -> entity != player }

        val target = hit?.hitEntity?.location
            ?: hit?.hitPosition?.toLocation(player.world)
            ?: eyeLocation.add(direction.multiply(100.0))
        player.world.strikeLightning(target)
    }

}