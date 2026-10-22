package rkdgus.Event
/*

만들어놓고 너무 버그가 많아서 포기함
//TODO: 나중에 시간나면 다시 만들기
package rkdgus.Event

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.scheduler.BukkitTask
import rkdgus.Kotlin
import rkdgus.Listener.craftBukkit

@info(description = "무작위 금지 행동을 선택하고, 해당 행동이 발생하면 번개와 위더 및 연쇄 폭발을 일으킵니다.")
class Death : craftBukkit(),Runnable {

    internal lateinit var bukkitTask: BukkitTask

    private var currentEvent = RandomEvent.entries.random()

    private var registeredListener: Listener? = null

    private var actionBarTask: BukkitTask? = null

    private var eventTimeoutTask: BukkitTask? = null

    private var rouletteTask: BukkitTask? = null

    private var started = false

    private data class Explosion(
        val location: Location,
        val maxTicks: Int,
        var ticks: Int = 0
    )

    private val explosions = mutableListOf<Explosion>()

    enum class RandomEvent(
        val displayName: String,
        val eventClass: Class<out Event>
    ) {
        BLOCK_BREAK("블럭 부수기", BlockBreakEvent::class.java),

        BLOCK_PLACE("블럭 설치", BlockPlaceEvent::class.java),

        ITEM_DROP("아이템 버리기", PlayerDropItemEvent::class.java),

        ITEM_PICKUP("아이템 줍기", EntityPickupItemEvent::class.java),

        ITEM_CONSUME("음식 먹기", PlayerItemConsumeEvent::class.java),

        ITEM_DAMAGE("아이템 사용", PlayerItemDamageEvent::class.java),

        BUCKET_FILL("양동이 채우기", PlayerBucketFillEvent::class.java),

        BUCKET_EMPTY("양동이 비우기", PlayerBucketEmptyEvent::class.java),

        SHOOT_BOW("활 발사", EntityShootBowEvent::class.java),

        ENTITY_DAMAGE("엔티티 공격", EntityDamageByEntityEvent::class.java),

        INVENTORY_OPEN("인벤토리 열기", InventoryOpenEvent::class.java),

        INVENTORY_CLOSE("인벤토리 닫기", InventoryCloseEvent::class.java),

        SWAP_HAND("손 바꾸기", PlayerSwapHandItemsEvent::class.java),

        BED_ENTER("침대 들어가기", PlayerBedEnterEvent::class.java),

        BED_LEAVE("침대 나오기", PlayerBedLeaveEvent::class.java),

        ADVANCEMENT("발전과제 달성", PlayerAdvancementDoneEvent::class.java),

        CHANGED_WORLD("월드 이동", PlayerChangedWorldEvent::class.java),

        PORTAL("차원 이동", PlayerPortalEvent::class.java),

        EGG_THROW("달걀 던지기", PlayerEggThrowEvent::class.java),

        EXP_CHANGE("경험치 획득", PlayerExpChangeEvent::class.java),

        SMELT("화로 굽기", FurnaceSmeltEvent::class.java),

        CRAFT("아이템 제작", CraftItemEvent::class.java),

        ENCHANT("인챈트", EnchantItemEvent::class.java),

        PREPARE_ANVIL("모루 사용", PrepareAnvilEvent::class.java),
    }

    override fun run() {

        if (!started) {
            started = true
            startRoulette()
        }

        val iterator = explosions.iterator()

        while (iterator.hasNext()) {
            val explosion = iterator.next()

            explosion.ticks++

            val radiusPerTick = 0.5
            val pointPerCircum = 6.0

            val radius = radiusPerTick * explosion.ticks
            val circum = 2.0 * Math.PI * radius

            val pointsCount = maxOf(1, (circum / pointPerCircum).toInt())
            val angle = 360.0 / pointsCount

            val world = explosion.location.world
            val y = explosion.location.y

            for (i in 0 until pointsCount) {
                val rad = Math.toRadians(i * angle)

                val x = -kotlin.math.sin(rad)
                val z = kotlin.math.cos(rad)

                world.createExplosion(
                    explosion.location.x + x * radius,
                    y,
                    explosion.location.z + z * radius,
                    4.0F,
                    false,
                    true
                )
            }

            if (explosion.ticks >= explosion.maxTicks) {
                iterator.remove()
            }
        }
    }

    private fun startRoulette() {
        registeredListener?.let {
            HandlerList.unregisterAll(it)
        }
        registeredListener = null

        actionBarTask?.cancel()
        actionBarTask = null

        eventTimeoutTask?.cancel()
        eventTimeoutTask = null

        rouletteTask?.cancel()

        var count = 0
        var delay = 1L

        fun nextSpin() {

            rouletteTask = Bukkit.getScheduler().runTaskLater(
                Kotlin.bukkit(),
                Runnable {

                    val previousEvent = currentEvent
                    if (RandomEvent.entries.size > 1) {
                        do {
                            currentEvent = RandomEvent.entries.random()
                        } while (currentEvent == previousEvent)
                    }

                    Bukkit.getOnlinePlayers().forEach { player ->
                        val hard = isHardEvent(currentEvent)
                        val color = if (hard) "§c§l" else "§e"

                        player.sendActionBar(
                            Component.text("§c금지된 행동 §f: $color${currentEvent.displayName}")
                        )

                        player.playSound(
                            player.location,
                            Sound.BLOCK_NOTE_BLOCK_HAT,
                            0.8f,
                            2.0f
                        )
                    }
                    count++

                    if (count >= 25) {

                        Bukkit.getOnlinePlayers().forEach { player ->

                            val hard = isHardEvent(currentEvent)
                            val color = if (hard) "§c§l" else "§e"

                            player.sendActionBar(
                                Component.text("§c금지된 행동 §f: $color${currentEvent.displayName}")
                            )

                            player.playSound(
                                player.location,
                                if (hard) Sound.ENTITY_WITHER_SPAWN else Sound.BLOCK_NOTE_BLOCK_HAT,
                                if (hard) 1.2f else 0.8f,
                                if (hard) 0.6f else 2.0f
                            )
                            selectEvent()
                            return@Runnable
                        }

                    }

                    if (delay < 2L)
                        delay += 2L

                    nextSpin()

                },
                delay
            )
        }

        nextSpin()
    }

    private fun selectEvent() {

        val hard = isHardEvent(currentEvent)
        val color = if (hard) "§c§l" else "§e"

        Bukkit.getOnlinePlayers().forEach {
            it.sendActionBar(
                Component.text("§c금지된 행동 §f: $color${currentEvent.displayName}")
            )

            it.playSound(
                it.location,
                if (hard) Sound.ENTITY_WITHER_SPAWN else Sound.ENTITY_PLAYER_LEVELUP,
                if (hard) 1.3f else 1f,
                if (hard) 0.7f else 1f
            )
        }

        startActionBar()
        startTimeout()
        registerListener()
    }

    private fun isHardEvent(event: RandomEvent): Boolean {
        return when (event) {
            RandomEvent.ENCHANT,
            RandomEvent.EGG_THROW,
            RandomEvent.SHOOT_BOW,
            RandomEvent.PREPARE_ANVIL -> true
            else -> false
        }
    }



    private fun registerListener() {

        registeredListener?.let {
            HandlerList.unregisterAll(it)
        }

        val listener = object : Listener {}

        Bukkit.getPluginManager().registerEvent(
            currentEvent.eventClass,
            listener,
            EventPriority.NORMAL,
            { _, event ->

                val location = when (event) {

                    is BlockBreakEvent -> event.block.location
                    is BlockPlaceEvent -> event.block.location

                    is PlayerEvent -> event.player.location

                    is EntityEvent -> event.entity.location

                    is InventoryEvent -> event.view.player.location

                    is CraftItemEvent -> event.whoClicked.location

                    is EnchantItemEvent -> event.enchanter.location

                    is PrepareAnvilEvent -> event.view.player.location

                    is BrewEvent -> event.block.location

                    is FurnaceSmeltEvent -> event.block.location

                    else -> return@registerEvent
                }

                val world = location.world

                if (isHardEvent(currentEvent)) {
                    world.strikeLightning(location)

                    world.spawnEntity(location, EntityType.WITHER)
                }

                val maxTicks = when (currentEvent) {
                    RandomEvent.ENCHANT -> 100
                    RandomEvent.EGG_THROW -> 250
                    RandomEvent.SHOOT_BOW -> 100
                    RandomEvent.PREPARE_ANVIL -> 100
                    else -> 5
                }

                explosions += Explosion(location.clone(), maxTicks)

                registeredListener?.let {
                    HandlerList.unregisterAll(it)
                }
                registeredListener = null

                eventTimeoutTask?.cancel()

                Bukkit.getScheduler().runTask(
                    Kotlin.bukkit(),
                    Runnable {
                        startRoulette()
                    }
                )
            },
            Kotlin.bukkit()
        )

        registeredListener = listener
    }

    private fun startActionBar() {

        actionBarTask?.cancel()

        actionBarTask = Bukkit.getScheduler().runTaskTimer(
            Kotlin.bukkit(),
            Runnable {

                Bukkit.getOnlinePlayers().forEach {

                    val color = if (isHardEvent(currentEvent)) "§c§l" else "§e"

                    it.sendActionBar(
                        Component.text("§c금지된 행동 §f: $color${currentEvent.displayName}")
                    )
                }

            },
            0L,
            20L
        )
    }

    private fun startTimeout() {

        eventTimeoutTask?.cancel()

        eventTimeoutTask = Bukkit.getScheduler().runTaskLater(
            Kotlin.bukkit(),
            Runnable {

                Bukkit.broadcastMessage("§7시간 초과!")

                startRoulette()

            },
            20L * 60L * 3L
        )
    }
}


 */