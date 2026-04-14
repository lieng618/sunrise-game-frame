package org.sunrise.game.game.logic.system;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.DataModule;
import org.sunrise.game.game.modules.MinerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MinerSystem extends BaseSystem {
    @Getter
    private HashMap<String, Integer> ranks = new HashMap<>();
    @Getter
    private List<String> sortRanks = new ArrayList<>();
    @Override
    public void load() {
        getDbData("ranks", new TypeReference<HashMap<String, Integer>>() {
        }, value -> {
            ranks = value;
            rebuildSortRanks();
        });
    }

    @Override
    public void save() {
        putDbData("ranks", ranks);
    }

    private void rebuildSortRanks() {
        sortRanks = new ArrayList<>(ranks.keySet());
        sortRanks.sort((name1, name2) -> {
            int level1 = ranks.getOrDefault(name1, 0);
            int level2 = ranks.getOrDefault(name2, 0);
            return Integer.compare(level2, level1);
        });
    }

    public void update(HumanObject humanObject) {
        String name = humanObject.getModule(DataModule.class).getName();
        int newLevel = humanObject.getModule(MinerModule.class).getLevelIndex();

        ranks.put(name, newLevel);

        if (!sortRanks.contains(name)) {
            sortRanks.add(name);
        }

        sortRanks.sort((name1, name2) -> {
            int level1 = ranks.get(name1);
            int level2 = ranks.get(name2);
            return Integer.compare(level2, level1);
        });
    }

}
