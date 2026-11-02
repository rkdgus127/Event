package rkdgus.Listener;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import rkdgus.Info.Info;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class BukkitLoader {

    private static final String EVENT_PACKAGE = "rkdgus.Event";
    private static final String EVENT_PATH = "rkdgus/Event/";

    private final Plugin plugin;
    private final Map<String, Class<? extends craftBukkit>> eventClasses = new TreeMap<>();
    private final Map<String, List<Class<? extends craftBukkit>>> packageClasses = new TreeMap<>();
    private final Map<String, Object> activeEvents = new HashMap<>();
    private final Map<String, BukkitTask> activeTasks = new HashMap<>();

    private BukkitLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public static BukkitLoader loadAll(Plugin plugin) {
        BukkitLoader loader = new BukkitLoader(plugin);
        loader.discoverEvents();
        return loader;
    }

    public List<String> getEventNames() {
        List<String> names = new java.util.ArrayList<>(eventClasses.keySet());
        packageClasses.keySet().stream()
                .map(BukkitLoader::getPackageAlias)
                .forEach(names::add);
        return names.stream().distinct().sorted().toList();
    }

    public List<String> getTabCompletionEventNames() {
        List<String> names = eventClasses.values().stream()
                .filter(eventClass -> eventClass.getPackageName().equals(EVENT_PACKAGE))
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        packageClasses.keySet().stream()
                .map(BukkitLoader::getPackageAlias)
                .forEach(names::add);
        return names.stream().distinct().sorted().toList();
    }

    public List<String> getActiveTabCompletionEventNames() {
        List<String> names = activeEvents.keySet().stream()
                .filter(name -> {
                    Class<? extends craftBukkit> eventClass = eventClasses.get(name);
                    return eventClass != null && eventClass.getPackageName().equals(EVENT_PACKAGE);
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        packageClasses.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(eventClass -> activeEvents.containsKey(eventClass.getSimpleName())))
                .map(Map.Entry::getKey)
                .map(BukkitLoader::getPackageAlias)
                .forEach(names::add);
        return names.stream().distinct().sorted().toList();
    }

    public String getEventDescription(String requestedName) {
        Class<? extends craftBukkit> eventClass = eventClasses.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(requestedName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (eventClass == null) {
            return null;
        }

        Info annotation = eventClass.getAnnotation(Info.class);
        return annotation == null ? null : annotation.description();
    }

    public boolean startEvent(String requestedName) throws ReflectiveOperationException {
        String eventName = eventClasses.keySet().stream()
                .filter(name -> name.equalsIgnoreCase(requestedName))
                .findFirst()
                .orElse(null);
        List<Class<? extends craftBukkit>> selectedClasses = eventName == null
                ? findPackageClasses(requestedName)
                : List.of(eventClasses.get(eventName));
        if (selectedClasses.isEmpty()) {
            return false;
        }

        List<String> startedNames = new java.util.ArrayList<>();
        try {
            for (Class<? extends craftBukkit> eventClass : selectedClasses) {
                String className = eventClass.getSimpleName();
                if (!activeEvents.containsKey(className) && startSingleEvent(className, eventClass)) {
                    startedNames.add(className);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            for (String startedName : startedNames) {
                try {
                    stopSingleEvent(startedName);
                } catch (ReflectiveOperationException | RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
        return !startedNames.isEmpty();
    }

    private boolean startSingleEvent(String eventName, Class<? extends craftBukkit> eventClass)
            throws ReflectiveOperationException {
        if (activeEvents.containsKey(eventName)) {
            return false;
        }

        Object instance = eventClass.getDeclaredConstructor().newInstance();
        Listener listener = instance instanceof Listener value ? value : null;
        BukkitTask task = null;
        boolean listenerRegistered = false;

        try {
            if (listener != null) {
                Bukkit.getPluginManager().registerEvents(listener, plugin);
                listenerRegistered = true;
            }

            if (instance instanceof Runnable runnable) {
                task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, 0L, 1L);
                setTaskField(eventClass, instance, task);
            }

            activeEvents.put(eventName, instance);
            if (task != null) {
                activeTasks.put(eventName, task);
            }
            plugin.getLogger().info("이벤트 시작 : " + eventName);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (task != null) {
                task.cancel();
            }
            if (listenerRegistered) {
                HandlerList.unregisterAll(listener);
            }
            throw exception;
        }
    }

    public List<String> getActiveEventNames() {
        return List.copyOf(activeEvents.keySet());
    }

    public boolean stopEvent(String requestedName) throws ReflectiveOperationException {
        String eventName = activeEvents.keySet().stream()
                .filter(name -> name.equalsIgnoreCase(requestedName))
                .findFirst()
                .orElse(null);
        List<String> selectedNames = eventName == null
                ? findPackageClasses(requestedName).stream()
                        .map(Class::getSimpleName)
                        .filter(activeEvents::containsKey)
                        .toList()
                : List.of(eventName);
        if (selectedNames.isEmpty()) {
            return false;
        }

        ReflectiveOperationException reflectionFailure = null;
        RuntimeException runtimeFailure = null;
        for (String selectedName : selectedNames) {
            try {
                stopSingleEvent(selectedName);
            } catch (ReflectiveOperationException exception) {
                if (reflectionFailure == null) {
                    reflectionFailure = exception;
                } else {
                    reflectionFailure.addSuppressed(exception);
                }
            } catch (RuntimeException exception) {
                if (runtimeFailure == null) {
                    runtimeFailure = exception;
                } else {
                    runtimeFailure.addSuppressed(exception);
                }
            }
        }
        if (runtimeFailure != null) {
            if (reflectionFailure != null) {
                runtimeFailure.addSuppressed(reflectionFailure);
            }
            throw runtimeFailure;
        }
        if (reflectionFailure != null) {
            throw reflectionFailure;
        }
        return true;
    }

    private void stopSingleEvent(String eventName) throws ReflectiveOperationException {
        Object instance = activeEvents.get(eventName);
        try {
            try {
                var shutdown = instance.getClass().getDeclaredMethod("shutdown");
                shutdown.setAccessible(true);
                shutdown.invoke(instance);
            } catch (NoSuchMethodException ignored) {
                // Listener and repeating task cleanup below is sufficient for simple events.
            }
        } finally {
            BukkitTask task = activeTasks.remove(eventName);
            if (task != null) {
                task.cancel();
            }
            if (instance instanceof Listener listener) {
                HandlerList.unregisterAll(listener);
            }
            activeEvents.remove(eventName);
        }

        plugin.getLogger().info("이벤트 종료 : " + eventName);
    }

    private void discoverEvents() {
        try {
            URL codeLocation = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            Path path = Path.of(codeLocation.toURI());

            if (Files.isDirectory(path)) {
                discoverFromDirectory(path.resolve(EVENT_PATH));
            } else {
                discoverFromJar(path.toFile());
            }
        } catch (URISyntaxException | IOException exception) {
            throw new IllegalStateException("Event 패키지 검색 실패", exception);
        }
    }

    private void discoverFromDirectory(Path eventDirectory) throws IOException {
        if (!Files.isDirectory(eventDirectory)) {
            return;
        }

        try (var files = Files.walk(eventDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().contains("$"))
                    .map(eventDirectory::relativize)
                    .map(Path::toString)
                    .map(relativePath -> EVENT_PACKAGE + "."
                            + relativePath.substring(0, relativePath.length() - 6)
                                    .replace(File.separatorChar, '.'))
                    .forEach(this::registerEventClass);
        }
    }

    private void discoverFromJar(File file) throws IOException {
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(EVENT_PATH)
                        && name.endsWith(".class")
                        && !name.contains("$")) {
                    registerEventClass(name.substring(0, name.length() - 6).replace('/', '.'));
                }
            }
        }
    }

    private void registerEventClass(String className) {
        try {
            Class<?> candidate = Class.forName(className, false, plugin.getClass().getClassLoader());
            if (candidate != craftBukkit.class
                    && craftBukkit.class.isAssignableFrom(candidate)
                    && !candidate.isInterface()
                    && !candidate.isEnum()
                    && !candidate.isAnnotation()
                    && !candidate.isSynthetic()
                    && !Modifier.isAbstract(candidate.getModifiers())) {
                @SuppressWarnings("unchecked")
                Class<? extends craftBukkit> eventClass = (Class<? extends craftBukkit>) candidate;
                eventClasses.putIfAbsent(candidate.getSimpleName(), eventClass);
                packageClasses.computeIfAbsent(candidate.getPackageName(), ignored -> new java.util.ArrayList<>())
                        .add(eventClass);
            }
        } catch (ClassNotFoundException | LinkageError exception) {
            plugin.getLogger().warning("이벤트 클래스 검색 실패 : " + className + " (" + exception.getMessage() + ")");
        }
    }

    private List<Class<? extends craftBukkit>> findPackageClasses(String requestedName) {
        return packageClasses.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(requestedName)
                        || getPackageAlias(entry.getKey()).equalsIgnoreCase(requestedName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
    }

    private static String getPackageAlias(String packageName) {
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private void setTaskField(Class<?> eventClass, Object instance, BukkitTask task)
            throws ReflectiveOperationException {
        try {
            Field field = eventClass.getDeclaredField("bukkitTask");
            field.setAccessible(true);
            field.set(instance, task);
        } catch (NoSuchFieldException ignored) {
            // Runnable event classes may not need to retain their scheduled task.
        }
    }
}
