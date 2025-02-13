package org.by1337.bmenu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;
import org.by1337.blib.chat.util.Message;
import org.by1337.blib.util.SpacedNameKey;
import org.by1337.blib.util.collection.SpacedNameRegistry;
import org.by1337.bmenu.factory.MenuFactory;
import org.by1337.bmenu.util.ObjectUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class MenuLoader implements Listener {
    private final MenuRegistry registry;
    private final File homeDir;
    private final Plugin plugin;
    private final Logger logger;
    private final Message message;
    private final SpacedNameRegistry<MenuConfig> menuRegistry;
    private final String defaultSpace;

    public MenuLoader(File homeDir, Plugin plugin) {
        this.homeDir = homeDir;
        homeDir.mkdirs();
        this.plugin = plugin;
        defaultSpace = plugin.getName().toLowerCase();
        logger = plugin.getSLF4JLogger();
        message = new Message(plugin.getLogger());
        registry = new MenuRegistry();
        registry.merge(MenuRegistry.DEFAULT_REGISTRY);
        menuRegistry = new SpacedNameRegistry<>();
    }

    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.getLoader() == this) {
                player.closeInventory();
            }
        }
        HandlerList.unregisterAll(this);
    }

    public void loadMenus() {
        loadMenus0(homeDir).stream().filter(config -> config.getId() != null).forEach(this::registerMenu);
    }

    private List<MenuConfig> loadMenus0(File dir) {
        List<MenuConfig> result = new ArrayList<>();
        for (File file : Objects.requireNonNull(dir.listFiles(), "Menu folder isn't exists")) {
            if (file.isDirectory()) {
                result.addAll(loadMenus0(file));
            }
            if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                try {
                    result.add(MenuFactory.load(file, this));
                } catch (Throwable t) {
                    logger.warn("Failed to load menu config File: {}", file.getPath(), t);
                }
            }
        }
        return result;
    }

    public Menu findAndCreate(String menuId, Player viewer, @Nullable Menu previousMenu) {
        MenuConfig menuConfig = findMenu(menuId);
        if (menuConfig == null) {
            throw new IllegalArgumentException("Menu not found: " + menuId);
        }
        return findAndCreate(menuConfig, viewer, previousMenu);
    }

    public Menu findAndCreate(SpacedNameKey menuId, Player viewer, @Nullable Menu previousMenu) {
        MenuConfig menuConfig = menuRegistry.find(menuId);
        if (menuConfig == null) {
            throw new IllegalArgumentException("Menu not found: " + menuId);
        }
        return findAndCreate(menuConfig, viewer, previousMenu);
    }

    public Menu findAndCreate(MenuConfig config, Player viewer, @Nullable Menu previousMenu) {
        if (config.getProvider() == null) {
            throw new IllegalArgumentException("Menu provider is null");
        }
        MenuRegistry.MenuCreator creator = registry.findCreator(config.getProvider());
        if (creator == null) {
            creator = registry.findCreatorByName(config.getProvider().getName().getName());
        }
        if (creator == null) {
            throw new IllegalArgumentException("Unknown menu provider " + config.getProvider().getName().getName());
        }
        return creator.createMenu(config, viewer, previousMenu);
    }

    public Menu create(String id, Player viewer, @Nullable Menu previousMenu) {
        MenuConfig cfg = findMenu(id);
        if (cfg == null) {
            throw new IllegalArgumentException("Unknown menu " + id);
        }
        MenuRegistry.MenuCreator creator = ObjectUtil.requireNonNullElseGet(
                registry.findCreator(cfg.getProvider()),
                () -> registry.findCreatorByName(cfg.getProvider().getName().getName())
        );
        if (creator == null) {
            throw new IllegalArgumentException("Unknown menu provider " + cfg.getProvider());
        }
        return creator.createMenu(cfg, viewer, previousMenu);
    }

    @Nullable
    public MenuConfig findMenuByName(String name) {
        return menuRegistry.find(SpacedNameKey.fromString(name, defaultSpace));
    }

    @Nullable
    public MenuConfig findMenu(String name) {
        return menuRegistry.find(SpacedNameKey.fromString(name, defaultSpace));
    }

    @Nullable
    public MenuConfig findMenu(SpacedNameKey key) {
        return menuRegistry.find(key);
    }

    public int getMenuCount() {
        return menuRegistry.size();
    }

    public Collection<SpacedNameKey> getMenus() {
        return menuRegistry.keySet();
    }

    public void registerMenu(MenuConfig config) {
        if (config.getId() == null) {
            throw new IllegalArgumentException("Can't register anonymous menu config");
        }
        if (menuRegistry.has(config.getId())) {
            throw new IllegalArgumentException("Menu config already exists");
        } else {
            menuRegistry.put(config.getId(), config);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu && menu.getLoader() == this) {
            event.setCancelled(true);
            if (System.currentTimeMillis() - menu.getLastClickTime() < 100) return;
            menu.onClick(event);
        }
    }

    @EventHandler
    public void onClick(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu && menu.getLoader() == this) {
            event.setCancelled(true);
            if (System.currentTimeMillis() - menu.getLastClickTime() < 100) return;
            menu.onClick(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu && menu.getLoader() == this) {
            menu.onClose(event);
        }
    }

    public File getHomeDir() {
        return homeDir;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Logger getLogger() {
        return logger;
    }

    public Message getMessage() {
        return message;
    }

    public MenuRegistry getRegistry() {
        return registry;
    }
}
