package com.Leeinx.ximultilogin.command;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.config.ConfigManager;
import com.Leeinx.ximultilogin.config.MessageManager;
import com.Leeinx.ximultilogin.guard.IdentityGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * XiMultiLogin 命令执行器
 * 处理插件的所有指令
 */
public class XiCommandExecutor implements CommandExecutor {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final XiMultiLogin plugin;
    private final IdentityGuard identityGuard;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    /**
     * 构造 XiCommandExecutor
     *
     * @param plugin 插件实例
     */
    public XiCommandExecutor(XiMultiLogin plugin) {
        this.plugin = plugin;
        this.identityGuard = plugin.getIdentityGuard();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ximultilogin")) {
            return false;
        }

        // 处理子命令
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "setauth":
                return handleSetAuth(sender, args);
            case "getauth":
                return handleGetAuth(sender, args);
            case "reload":
                return handleReload(sender);
            case "info":
                return handleInfo(sender);
            case "allowcracked":
                return handleAllowCracked(sender, args);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    /**
     * 处理设置玩家认证方式的命令
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 命令执行是否成功
     */
    private boolean handleSetAuth(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission("ximultilogin.setauth")) {
            sender.sendMessage(messageManager.getMessage("error.no_permission"));
            return true;
        }

        // 检查参数
        if (args.length < 3) {
            sender.sendMessage(messageManager.getMessage("error.invalid_args"));
            return true;
        }

        String playerName = args[1];
        String authType = args[2];

        // 验证认证类型
        if (!isValidAuthType(authType)) {
            sender.sendMessage(messageManager.getMessage("error.invalid_args"));
            return true;
        }

        // 获取玩家的UUID
        UUID playerUUID = null;
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            playerUUID = player.getUniqueId();
        } else {
            // 尝试从数据库获取
            playerUUID = identityGuard.getUUID(playerName);
        }

        if (playerUUID == null) {
            sender.sendMessage(messageManager.getMessage("error.player_not_found", "player", playerName));
            return true;
        }

        // 设置认证方式
        boolean success = identityGuard.updateAuthProvider(playerName, playerUUID, authType);
        if (success) {
            sender.sendMessage(messageManager.getMessage("command.set_auth", "player", playerName, "auth", authType));
            LOGGER.info("XiMultiLogin: Admin " + sender.getName() + " set auth type for " + playerName + " to " + authType);
        } else {
            sender.sendMessage(messageManager.getMessage("error.config_error", "reason", "设置认证方式失败"));
        }

        return true;
    }

    /**
     * 处理获取玩家认证方式的命令
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 命令执行是否成功
     */
    private boolean handleGetAuth(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission("ximultilogin.getauth")) {
            sender.sendMessage(messageManager.getMessage("error.no_permission"));
            return true;
        }

        // 检查参数
        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("error.invalid_args"));
            return true;
        }

        String playerName = args[1];

        // 获取认证方式
        String authType = identityGuard.getAuthProvider(playerName);
        if (authType != null) {
            sender.sendMessage(messageManager.getMessage("command.get_auth", "player", playerName, "auth", authType));
        } else {
            sender.sendMessage(messageManager.getMessage("error.player_not_found", "player", playerName));
        }

        return true;
    }

    /**
     * 处理重新加载配置的命令
     *
     * @param sender 命令发送者
     * @return 命令执行是否成功
     */
    private boolean handleReload(CommandSender sender) {
        // 检查权限
        if (!sender.hasPermission("ximultilogin.reload")) {
            sender.sendMessage(messageManager.getMessage("error.no_permission"));
            return true;
        }

        // 重新加载配置
        configManager.loadConfig();
        messageManager.reloadMessages();
        sender.sendMessage(messageManager.getMessage("command.reloaded"));
        LOGGER.info("XiMultiLogin: Config reloaded by " + sender.getName());

        return true;
    }

    /**
     * 处理显示插件信息的命令
     *
     * @param sender 命令发送者
     * @return 命令执行是否成功
     */
    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage(messageManager.getMessage("other.info.title"));
        sender.sendMessage(messageManager.getMessage("other.info.version"));
        sender.sendMessage(messageManager.getMessage("other.info.author"));
        sender.sendMessage(messageManager.getMessage("other.info.description"));
        sender.sendMessage(messageManager.getMessage("other.info.command"));
        sender.sendMessage(messageManager.getMessage("other.info.footer"));
        return true;
    }

    /**
     * 处理设置是否允许盗版玩家的命令
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 命令执行是否成功
     */
    private boolean handleAllowCracked(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission("ximultilogin.allowcracked")) {
            sender.sendMessage(messageManager.getMessage("error.no_permission"));
            return true;
        }

        // 检查参数
        if (args.length == 1) {
            // 查看当前设置
            boolean currentValue = configManager.isAllowCracked();
            sender.sendMessage(messageManager.getMessage("command.get_allow_cracked", "value", currentValue ? "开启" : "关闭"));
            return true;
        } else if (args.length == 2) {
            // 设置新值
            String valueStr = args[1].toLowerCase();
            boolean newValue;

            if (valueStr.equals("true") || valueStr.equals("1") || valueStr.equals("on")) {
                newValue = true;
            } else if (valueStr.equals("false") || valueStr.equals("0") || valueStr.equals("off")) {
                newValue = false;
            } else {
                sender.sendMessage(messageManager.getMessage("error.invalid_args"));
                return true;
            }

            // 设置值
            configManager.setAllowCracked(newValue);
            sender.sendMessage(messageManager.getMessage("command.set_allow_cracked", "value", newValue ? "开启" : "关闭"));
            LOGGER.info("XiMultiLogin: Admin " + sender.getName() + " set allow_cracked to " + newValue);
            return true;
        } else {
            sender.sendMessage(messageManager.getMessage("error.invalid_args"));
            return true;
        }
    }

    /**
     * 发送帮助信息
     *
     * @param sender 命令发送者
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(messageManager.getMessage("other.help_menu.title"));
        sender.sendMessage(messageManager.getMessage("other.help_menu.setauth"));
        sender.sendMessage(messageManager.getMessage("other.help_menu.getauth"));
        sender.sendMessage(messageManager.getMessage("other.help_menu.allowcracked"));
        sender.sendMessage(messageManager.getMessage("other.help_menu.allowcracked_status"));
        sender.sendMessage(messageManager.getMessage("other.help_menu.reload"));
        sender.sendMessage(messageManager.getMessage("other.help_menu.info"));
        sender.sendMessage(messageManager.getMessage("other.help_menu.footer"));
    }

    /**
     * 验证认证类型是否有效
     *
     * @param authType 认证类型
     * @return 是否有效
     */
    private boolean isValidAuthType(String authType) {
        // 检查是否为MOJANG
        if (authType.equalsIgnoreCase("MOJANG")) {
            return true;
        }
        
        // 检查是否为配置中的外置登录名称
        List<ConfigManager.ProviderConfig> providers = configManager.getPipelineConfig();
        for (ConfigManager.ProviderConfig provider : providers) {
            if (provider.getName().equals(authType)) {
                return true;
            }
        }
        
        return false;
    }
}
