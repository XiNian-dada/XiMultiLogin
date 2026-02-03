package com.Leeinx.ximultilogin.command;

import com.Leeinx.ximultilogin.XiMultiLogin;
import com.Leeinx.ximultilogin.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * XiMultiLogin 标签补全器
 * 为插件的指令提供 TAB 自动补全功能
 */
public class XiTabCompleter implements TabCompleter {

    private final ConfigManager configManager;

    /**
     * 构造 XiTabCompleter
     *
     * @param plugin 插件实例
     */
    public XiTabCompleter(XiMultiLogin plugin) {
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!command.getName().equalsIgnoreCase("ximultilogin")) {
            return completions;
        }

        // 处理子命令补全
        if (args.length == 1) {
            // 补全子命令
            List<String> subCommands = new ArrayList<>();
            subCommands.add("setauth");
            subCommands.add("getauth");
            subCommands.add("reload");
            subCommands.add("info");
            subCommands.add("allowcracked");

            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            // 补全玩家名或布尔值
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("setauth") || subCommand.equals("getauth")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (subCommand.equals("allowcracked")) {
                // 补全布尔值
                List<String> booleanValues = new ArrayList<>();
                booleanValues.add("true");
                booleanValues.add("false");

                for (String value : booleanValues) {
                    if (value.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(value);
                    }
                }
            }
        } else if (args.length == 3) {
            // 补全认证类型
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("setauth")) {
                List<String> authTypes = new ArrayList<>();
                // 添加MOJANG
                authTypes.add("MOJANG");
                // 添加配置中的YGGDRASIL提供者的name字段值
                List<ConfigManager.ProviderConfig> providers = configManager.getPipelineConfig();
                for (ConfigManager.ProviderConfig provider : providers) {
                    if (provider.getType().equalsIgnoreCase("YGGDRASIL")) {
                        authTypes.add(provider.getName());
                    }
                }

                for (String authType : authTypes) {
                    if (authType.toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(authType);
                    }
                }
            }
        }

        return completions;
    }
}
