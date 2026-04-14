package org.sunrise.game.utils;

import com.github.yitter.contract.IdGeneratorException;
import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import org.sunrise.game.log.LogCore;

public class IdGenerator {
    private static IdGeneratorOptions options;

    public static void init(int WorkerId) {
        if (options == null) {
            options = new IdGeneratorOptions((short) WorkerId); // 节点id 限制为1-4096
            options.WorkerIdBitLength = 12; // 2^12-1，即最多支持4096个节点。
            options.SeqBitLength = 10; // 限制每毫秒生成的ID个数。若生成速度超过5万个/秒，建议加大 SeqBitLength 到 10。
            options.BaseTime = 1727712000000L; // 基础时间，设定为2024-10-01 00:00:00
            YitIdHelper.setIdGenerator(options);
            LogCore.ServerStartUp.info("IdGenerator init, WorkerId = { {} }", WorkerId);
        }
    }

    public static long getId() {
        try {
            return YitIdHelper.nextId();
        } catch (IdGeneratorException e) {
            LogCore.ServerStartUp.error("IdGenerator, failed");
        }
        return 0L;
    }
}
