package rkdgus.Event;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import rkdgus.Info.Info;
import rkdgus.Listener.craftBukkit;

import java.util.HashSet;
import java.util.Set;

@Info(description = "블럭 드랍율 10배")
public class Block extends craftBukkit {

    // 플레이어가 설치한 블럭 저장
    public static Set<String> placedBlocks = new HashSet<>();

    public static Set<String> interactedBlocks = new HashSet<>();


    // 블럭 설치 감지
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {

        Location loc = e.getBlock().getLocation();

        String key = getBlockKey(loc);

        placedBlocks.add(key);
    }

    //인터렉션된 블럭 제거(상자복사버그 방지)
    @EventHandler
    public void PlayerOpenChest(PlayerInteractEvent e) {

        if (e.getClickedBlock() != BlockType.CHEST) {
            return;
        }

        Location loc = e.getClickedBlock().getLocation();

        String key = getInteractedBlockKey(loc);

        interactedBlocks.add(key);
    }


    // 블럭 파괴 감지
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {

        Location location = e.getBlock().getLocation();
        World world = e.getBlock().getWorld();

        String key = getBlockKey(location);
        String interactedKey = getInteractedBlockKey(location);


        // 플레이어가 설치한 블럭이면 일반 드랍
        if (placedBlocks.contains(key) || interactedBlocks.contains(interactedKey)) {

            placedBlocks.remove(key);
            interactedBlocks.remove(interactedKey);

            return;
        }


        // 기존 드랍 제거
        e.setDropItems(false);


        // 자연 블럭 10배 드랍
        for (ItemStack item : e.getBlock().getDrops(e.getPlayer().getInventory().getItemInMainHand())) {

            for (int i = 0; i < 10; i++) {

                world.dropItemNaturally(
                        location.clone().add(0.5, 0.5, 0.5),
                        item.clone()
                );

            }
        }
    }


    // 블럭 위치 저장용 키 생성
    private String getBlockKey(Location loc) {

        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }

    private String getInteractedBlockKey(Location loc) {

        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }
}