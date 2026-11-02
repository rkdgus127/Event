package rkdgus.Command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rkdgus.Listener.BukkitLoader;

import java.util.List;
import java.util.Locale;

public class komand implements CommandExecutor, TabCompleter {

    private final BukkitLoader eventLoader;

    public komand(BukkitLoader eventLoader) {
        this.eventLoader = eventLoader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2 || (!args[0].equalsIgnoreCase("start")
                && !args[0].equalsIgnoreCase("stop")
                && !args[0].equalsIgnoreCase("info"))) {
            sender.sendMessage("사용법: /event <start|stop|info> <이벤트 클래스명|패키지명>");
            return true;
        }

        boolean stopping = args[0].equalsIgnoreCase("stop");
        boolean showingInfo = args[0].equalsIgnoreCase("info");
        if (showingInfo) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("이벤트 정보는 플레이어만 확인할 수 있습니다.");
                return true;
            }

            String description = eventLoader.getEventDescription(args[1]);
            if (description == null) {
                if (eventLoader.getEventNames().stream().noneMatch(name -> name.equalsIgnoreCase(args[1]))) {
                    player.sendMessage("이벤트를 찾을 수 없습니다: " + args[1]);
                } else {
                    player.sendMessage(args[1] + " 이벤트에 등록된 정보가 없습니다.");
                }
            } else {
                player.sendMessage("§e[" + args[1] + "] §f" + description);
            }
            return true;
        }

        try {
            if (stopping) {
                if (eventLoader.stopEvent(args[1])) {
                    sender.sendMessage(args[1] + " 이벤트를 중지했습니다.");
                } else {
                    sender.sendMessage("실행 중인 이벤트를 찾을 수 없습니다: " + args[1]);
                }
            } else {
                if (eventLoader.startEvent(args[1])) {
                    sender.sendMessage(args[1] + " 이벤트를 시작했습니다.");
                } else if (eventLoader.getEventNames().stream().anyMatch(name -> name.equalsIgnoreCase(args[1]))) {
                    sender.sendMessage(args[1] + " 이벤트는 이미 실행 중입니다.");
                } else {
                    sender.sendMessage("이벤트를 찾을 수 없습니다: " + args[1]);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            sender.sendMessage("이벤트 " + (stopping ? "중지" : "시작") + "에 실패했습니다: " + args[1]);
            (stopping ? eventLoader.getActiveEventNames() : eventLoader.getEventNames()).stream()
                    .filter(name -> name.equalsIgnoreCase(args[1]))
                    .findFirst()
                    .ifPresent(name -> sender.getServer().getLogger()
                            .severe("이벤트 " + (stopping ? "중지" : "시작") + " 실패 : "
                                    + name + " (" + exception.getMessage() + ")"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("start", "stop", "info").stream()
                    .filter(action -> action.startsWith(prefix))
                    .toList();
        }
        if (args.length != 2
                || (!args[0].equalsIgnoreCase("start")
                && !args[0].equalsIgnoreCase("stop")
                && !args[0].equalsIgnoreCase("info"))) {
            return List.of();
        }

        boolean stopping = args[0].equalsIgnoreCase("stop");
        String prefix = args[1].toLowerCase(Locale.ROOT);
        List<String> candidates = stopping
                ? eventLoader.getActiveTabCompletionEventNames()
                : eventLoader.getTabCompletionEventNames();
        return candidates.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
