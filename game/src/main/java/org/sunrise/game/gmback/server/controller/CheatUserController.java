package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import org.sunrise.game.game.logic.gm.GmAbility;
import org.sunrise.game.game.logic.gm.GmCheatUser;
import org.sunrise.game.log.LogCore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金手指用户管理。
 */
public class CheatUserController extends BaseController {

    private final ConcurrentHashMap<String, GmCheatUser> cheatUsers = new ConcurrentHashMap<>();

    @Override
    public void load() {
        getDbData("cheatUsers", new TypeReference<List<GmCheatUser>>() {
        }, value -> {
            if (value != null) {
                cheatUsers.clear();
                for (GmCheatUser user : value) {
                    String key = targetKey(user.getUid(), user.getHumanId());
                    if (key != null) {
                        cheatUsers.put(key, user);
                    }
                }
            }
        });
    }

    @Override
    public void save() {
        putDbData("cheatUsers", new ArrayList<>(cheatUsers.values()));
    }

    public void list(Context ctx) {
        try {
            List<GmCheatUser> all = new ArrayList<>(cheatUsers.values());
            all.sort(Comparator.comparing(
                    GmCheatUser::getAddTime,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));

            String pageParam = ctx.queryParam("page");
            String sizeParam = ctx.queryParam("size");
            int page = pageParam == null ? 1 : Integer.parseInt(pageParam);
            int size = sizeParam == null ? 20 : Integer.parseInt(sizeParam);
            int total = all.size();
            int start = Math.max(0, (page - 1) * size);
            int end = Math.min(start + size, total);
            List<GmCheatUser> pageData = start < total
                    ? all.subList(start, end)
                    : List.of();

            Map<String, Object> data = new HashMap<>();
            data.put("list", pageData);
            data.put("total", total);
            success(ctx, data);
        } catch (NumberFormatException e) {
            fail(ctx, 400, "Invalid page or size parameter");
        }
    }

    public void listAbilities(Context ctx) {
        List<Map<String, String>> abilities = new ArrayList<>();
        for (GmAbility ability : GmAbility.all()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("command", ability.getCommand());
            item.put("label", ability.getLabel());
            item.put("usage", ability.getUsage());
            item.put("description", ability.getDescription());
            abilities.add(item);
        }
        success(ctx, abilities);
    }

    public void add(Context ctx) {
        String uid = trimToNull(getBodyParam(ctx, "uid", String.class));
        String humanId = trimToNull(getBodyParam(ctx, "humanId", String.class));
        String remark = trimToNull(getBodyParam(ctx, "remark", String.class));
        List<String> abilities = normalizeAbilities(getBodyParam(ctx, "abilities", List.class));

        if ((uid == null) == (humanId == null)) {
            fail(ctx, 400, "必须且只能填写玩家 UID 或游戏角色 ID");
            return;
        }
        if (abilities == null) {
            fail(ctx, 400, "请选择至少一项有效的 GM 能力");
            return;
        }

        String key = targetKey(uid, humanId);
        if (cheatUsers.containsKey(key)) {
            fail(ctx, 400, "该玩家已配置金手指权限");
            return;
        }

        long now = System.currentTimeMillis();
        GmCheatUser user = new GmCheatUser();
        user.setUid(uid);
        user.setHumanId(humanId);
        user.setAbilities(abilities);
        user.setRemark(remark);
        user.setAddTime(now);
        user.setUpdateTime(now);
        cheatUsers.put(key, user);

        save();
        syncCheatUsersToGame();
        success(ctx, null, "添加金手指用户成功");
    }

    public void update(Context ctx) {
        String uid = trimToNull(getBodyParam(ctx, "uid", String.class));
        String humanId = trimToNull(getBodyParam(ctx, "humanId", String.class));
        String remark = trimToNull(getBodyParam(ctx, "remark", String.class));
        List<String> abilities = normalizeAbilities(getBodyParam(ctx, "abilities", List.class));

        if ((uid == null) == (humanId == null)) {
            fail(ctx, 400, "必须且只能填写玩家 UID 或游戏角色 ID");
            return;
        }
        if (abilities == null) {
            fail(ctx, 400, "请选择至少一项有效的 GM 能力");
            return;
        }

        GmCheatUser user = cheatUsers.get(targetKey(uid, humanId));
        if (user == null) {
            fail(ctx, 404, "金手指用户不存在");
            return;
        }

        user.setAbilities(abilities);
        user.setRemark(remark);
        user.setUpdateTime(System.currentTimeMillis());
        save();
        syncCheatUsersToGame();
        success(ctx, null, "金手指权限更新成功");
    }

    public void remove(Context ctx) {
        String uid = trimToNull(getBodyParam(ctx, "uid", String.class));
        String humanId = trimToNull(getBodyParam(ctx, "humanId", String.class));
        if ((uid == null) == (humanId == null)) {
            fail(ctx, 400, "必须且只能填写玩家 UID 或游戏角色 ID");
            return;
        }

        GmCheatUser removed = cheatUsers.remove(targetKey(uid, humanId));
        if (removed == null) {
            fail(ctx, 404, "金手指用户不存在");
            return;
        }

        save();
        syncCheatUsersToGame();
        success(ctx, null, "移除金手指用户成功");
    }

    public void syncCheatUsersToGame() {
        List<GmCheatUser> users = new ArrayList<>(cheatUsers.values());
        Map<String, Object> data = new HashMap<>();
        data.put("users", users);
        sendMessageToAllGame("cheatUserList", data);
        LogCore.GmBackServer.debug("Synced cheat users to game, count = {}", users.size());
    }

    private List<String> normalizeAbilities(List<?> requested) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : requested) {
            GmAbility ability = GmAbility.fromCommand(String.valueOf(item));
            if (ability == null) {
                return null;
            }
            result.add(ability.getCommand());
        }
        return new ArrayList<>(result);
    }

    private String targetKey(String uid, String humanId) {
        String normalizedUid = trimToNull(uid);
        String normalizedHumanId = trimToNull(humanId);
        if (normalizedUid != null && normalizedHumanId == null) {
            return "uid:" + normalizedUid;
        }
        if (normalizedUid == null && normalizedHumanId != null) {
            return "human:" + normalizedHumanId;
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
