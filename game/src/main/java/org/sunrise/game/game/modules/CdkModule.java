package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.annotation.HumanModule;

import java.util.HashSet;
import java.util.Set;

/**
 * 玩家兑换码模块，记录已兑换的码
 */
@HumanModule
public class CdkModule extends BaseModule {

    private final Set<String> usedCodes = new HashSet<>();

    public CdkModule(String humanId) {
        super(humanId);
    }

    @Override
    public void load() {
        getDbData("usedCodes", new TypeReference<Set<String>>() {
        }, value -> {
            if (value != null) {
                usedCodes.clear();
                usedCodes.addAll(value);
            }
        });
    }

    @Override
    public void save() {
        putDbData("usedCodes", usedCodes);
    }

    public boolean hasUsed(String code) {
        return usedCodes.contains(code);
    }

    public void markUsed(String code) {
        usedCodes.add(code);
    }
}
