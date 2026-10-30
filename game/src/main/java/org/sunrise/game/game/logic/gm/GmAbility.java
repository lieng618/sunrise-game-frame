package org.sunrise.game.game.logic.gm;

import java.util.List;

/**
 * 聊天 GM 能力注册表。
 * 后台权限页面和游戏服鉴权共用这份定义。
 */
public enum GmAbility {
    SET_LEVEL("setlv", "设置等级", ".setlv <等级>", "设置当前角色等级"),
    SET_EXP("setexp", "设置经验", ".setexp <经验值>", "设置当前角色经验"),
    SET_NAME("setname", "设置名字", ".setname <名字>", "修改当前角色名字"),
    SET_SEX("setsex", "设置性别", ".setsex <性别>", "修改当前角色性别，0=女，1=男");

    private final String command;
    private final String label;
    private final String usage;
    private final String description;

    GmAbility(String command, String label, String usage, String description) {
        this.command = command;
        this.label = label;
        this.usage = usage;
        this.description = description;
    }

    public String getCommand() {
        return command;
    }

    public String getLabel() {
        return label;
    }

    public String getUsage() {
        return usage;
    }

    public String getDescription() {
        return description;
    }

    public static GmAbility fromCommand(String command) {
        if (command == null) {
            return null;
        }
        for (GmAbility ability : values()) {
            if (ability.command.equalsIgnoreCase(command)) {
                return ability;
            }
        }
        return null;
    }

    public static List<GmAbility> all() {
        return List.of(values());
    }
}
