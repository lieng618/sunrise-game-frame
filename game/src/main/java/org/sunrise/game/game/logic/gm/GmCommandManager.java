package org.sunrise.game.game.logic.gm;

import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.DataModule;

public class GmCommandManager {
    public static void handleGmCommand(HumanObject humanObject, String msg) {
        if (Tables.ConfigParam.getGMCmdStatus() != 1) {
            humanObject.sendTips("无效");
            return;
        }
        String[] parts = msg.substring(1).split("\\s+");
        if (parts.length < 1) {
            humanObject.sendTips("无效");
            return;
        }

        String cmd = parts[0].toLowerCase();
        DataModule dataModule = humanObject.getModule(DataModule.class);

        switch (cmd) {
            case "setlv":
                if (parts.length < 2) {
                    humanObject.sendTips("[GM] 用法: .setlv <等级>");
                    return;
                }
                try {
                    int level = Integer.parseInt(parts[1]);
                    dataModule.changeLevel(level);
                    humanObject.sendTips("[GM] 等级已设置为 " + level);
                } catch (NumberFormatException e) {
                    humanObject.sendTips("[GM] 等级必须是数字");
                }
                break;
            case "setexp":
                if (parts.length < 2) {
                    humanObject.sendTips("[GM] 用法: .setexp <经验值>");
                    return;
                }
                try {
                    int exp = Integer.parseInt(parts[1]);
                    dataModule.changeExp(exp);
                    humanObject.sendTips("[GM] 经验值已设置为 " + exp);
                } catch (NumberFormatException e) {
                    humanObject.sendTips("[GM] 经验值必须是数字");
                }
                break;
            case "setname":
                if (parts.length < 2) {
                    humanObject.sendTips("[GM] 用法: .setname <名字>");
                    return;
                }
                String name = parts[1];
                dataModule.changeName(name);
                humanObject.sendTips("[GM] 名字已设置为 " + name);
                break;
            case "setsex":
                if (parts.length < 2) {
                    humanObject.sendTips("[GM] 用法: .setsex <性别(0=女,1=男)>");
                    return;
                }
                try {
                    int sex = Integer.parseInt(parts[1]);
                    if (sex != 0 && sex != 1) {
                        humanObject.sendTips("[GM] 性别必须是0(女)或1(男)");
                        return;
                    }
                    dataModule.changeSex(sex);
                    humanObject.sendTips("[GM] 性别已设置为 " + (sex == 0 ? "女" : "男"));
                } catch (NumberFormatException e) {
                    humanObject.sendTips("[GM] 性别必须是数字");
                }
                break;
            case "help":
                humanObject.sendTips("[GM] 可用指令: .setlv, .setexp, .setname, .setsex, .help");
                break;
            default:
                humanObject.sendTips("[GM] 未知指令: " + cmd + ", 输入 .help 查看可用指令");
                break;
        }
    }
}
