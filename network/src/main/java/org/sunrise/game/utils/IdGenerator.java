package org.sunrise.game.utils;

import com.github.yitter.contract.IdGeneratorException;
import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import org.sunrise.game.log.LogCore;

import java.security.SecureRandom;

public class IdGenerator {
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static IdGeneratorOptions options;

    public static void init(int workerId) {
        if (options == null) {
            options = new IdGeneratorOptions((short) workerId); // 节点id 限制为1-4096
            options.WorkerIdBitLength = 12; // 2^12-1，即最多支持4096个节点。
            options.SeqBitLength = 10; // 限制每毫秒生成的ID个数。若生成速度超过5万个/秒，建议加大 SeqBitLength 到 10。
            options.BaseTime = 1727712000000L; // 基础时间，设定为2024-10-01 00:00:00
            YitIdHelper.setIdGenerator(options);
            LogCore.RpcUtils.info("IdGenerator init, WorkerId = { {} }", workerId);
        }
    }

    public static long getId() {
        try {
            return YitIdHelper.nextId();
        } catch (IdGeneratorException e) {
            LogCore.RpcUtils.error("IdGenerator, failed");
        }
        return 0L;
    }

    /** 账号 uid：A + 8位随机字母数字 + Base62(雪花ID) */
    public static String getUid() {
        return "A" + randomAlphanumeric(8) + encodeBase62(getId());
    }

    private static String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62[RANDOM.nextInt(BASE62.length)]);
        }
        return sb.toString();
    }

    private static String encodeBase62(long value) {
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(BASE62[(int) (value % 62)]);
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
