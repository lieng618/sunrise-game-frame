package org.sunrise.game.game.logic.gm;

import org.sunrise.game.game.human.HumanObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 游戏服当前生效的金手指用户快照。
 */
public final class GmCheatUserManager {

    private static volatile List<GmCheatUser> cheatUsers = List.of();

    private GmCheatUserManager() {
    }

    public static void replaceAll(List<GmCheatUser> users) {
        List<GmCheatUser> snapshot = new ArrayList<>();
        if (users != null) {
            for (GmCheatUser user : users) {
                if (user == null || (isBlank(user.getUid()) && isBlank(user.getHumanId()))) {
                    continue;
                }
                GmCheatUser copy = new GmCheatUser();
                copy.setUid(trimToNull(user.getUid()));
                copy.setHumanId(trimToNull(user.getHumanId()));
                copy.setRemark(user.getRemark());
                copy.setAddTime(user.getAddTime());
                copy.setUpdateTime(user.getUpdateTime());

                List<String> abilities = new ArrayList<>();
                if (user.getAbilities() != null) {
                    for (String command : user.getAbilities()) {
                        GmAbility ability = GmAbility.fromCommand(command);
                        if (ability != null && !abilities.contains(ability.getCommand())) {
                            abilities.add(ability.getCommand());
                        }
                    }
                }
                copy.setAbilities(abilities);
                snapshot.add(copy);
            }
        }
        cheatUsers = List.copyOf(snapshot);
    }

    public static boolean isCheatUser(HumanObject humanObject) {
        return !matchingRecords(humanObject).isEmpty();
    }

    public static boolean canUse(HumanObject humanObject, GmAbility ability) {
        if (ability == null) {
            return false;
        }
        for (GmCheatUser user : matchingRecords(humanObject)) {
            if (user.getAbilities() != null && user.getAbilities().contains(ability.getCommand())) {
                return true;
            }
        }
        return false;
    }

    public static Set<GmAbility> getAbilities(HumanObject humanObject) {
        EnumSet<GmAbility> result = EnumSet.noneOf(GmAbility.class);
        for (GmCheatUser user : matchingRecords(humanObject)) {
            if (user.getAbilities() == null) {
                continue;
            }
            for (String command : user.getAbilities()) {
                GmAbility ability = GmAbility.fromCommand(command);
                if (ability != null) {
                    result.add(ability);
                }
            }
        }
        return result;
    }

    private static List<GmCheatUser> matchingRecords(HumanObject humanObject) {
        if (humanObject == null) {
            return List.of();
        }
        String humanId = humanObject.getHumanId();
        String uid = humanObject.getConnectObject() == null
                ? null
                : humanObject.getConnectObject().getUid();
        List<GmCheatUser> result = new ArrayList<>();
        for (GmCheatUser user : cheatUsers) {
            boolean uidMatches = !isBlank(uid) && uid.equals(user.getUid());
            boolean humanMatches = !isBlank(humanId) && humanId.equals(user.getHumanId());
            if (uidMatches || humanMatches) {
                result.add(user);
            }
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
