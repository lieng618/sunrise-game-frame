package org.sunrise.game.game.logic.system;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.logic.mail.MailData;
import org.sunrise.game.log.LogCore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 兑换码系统，内存中仅保存当前有效的兑换码
 */
@GameSystem
public class CdkSystem extends BaseSystem {

    @Getter
    @Setter
    public static class CdkInfo {
        private String code;
        private long startTime;
        private long endTime;
        private int remainingCount;
        private int templateId;
        private List<MailData.MailAttachment> attachments;
    }

    private final HashMap<String, CdkInfo> activeCdks = new HashMap<>();

    public CdkInfo getCdkInfo(String code) {
        return activeCdks.get(code);
    }

    /**
     * 从 GM 后台同步有效兑换码列表
     */
    public void syncFromGm(String cdkListJson) {
        activeCdks.clear();
        if (cdkListJson == null || cdkListJson.isEmpty()) {
            return;
        }
        List<Map<String, Object>> list = JSON.parseObject(cdkListJson, new TypeReference<List<Map<String, Object>>>() {
        });
        if (list == null) {
            return;
        }
        for (Map<String, Object> item : list) {
            try {
                CdkInfo info = new CdkInfo();
                info.setCode((String) item.get("code"));
                info.setStartTime(((Number) item.get("startTime")).longValue());
                info.setEndTime(((Number) item.get("endTime")).longValue());
                info.setRemainingCount(((Number) item.get("remainingCount")).intValue());
                info.setTemplateId(item.get("templateId") != null ? ((Number) item.get("templateId")).intValue() : 1);
                Object attachmentsObj = item.get("attachments");
                if (attachmentsObj != null) {
                    String attachmentsJson = JSON.toJSONString(attachmentsObj);
                    List<MailData.MailAttachment> attachments = JSON.parseObject(
                            attachmentsJson, new TypeReference<List<MailData.MailAttachment>>() {
                            });
                    info.setAttachments(attachments);
                }
                if (info.getCode() != null && !info.getCode().isEmpty()) {
                    activeCdks.put(info.getCode(), info);
                }
            } catch (Exception e) {
                LogCore.GameServer.warn("Failed to parse CDK sync item: {}", item, e);
            }
        }
        LogCore.GameServer.debug("CDK sync completed, active count: {}", activeCdks.size());
    }

    /**
     * 尝试消耗一个兑换名额，成功返回 true
     */
    public boolean tryConsume(String code) {
        CdkInfo info = activeCdks.get(code);
        if (info == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < info.getStartTime() || now >= info.getEndTime()) {
            return false;
        }
        if (info.getRemainingCount() <= 0) {
            return false;
        }
        info.setRemainingCount(info.getRemainingCount() - 1);
        if (info.getRemainingCount() <= 0) {
            activeCdks.remove(code);
        }
        return true;
    }
}
