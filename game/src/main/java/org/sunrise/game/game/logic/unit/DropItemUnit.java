package org.sunrise.game.game.logic.unit;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.logic.attribute.AttributeContainer;

/**
 * 掉落物场景单位。怪物死亡后生成，玩家可在地图上拾取。
 * 拾取保护：掉落后前 {@link #PROTECT_SECONDS} 秒仅掉落者所属玩家可拾取，
 * 超时后全图玩家均可拾取。掉落物存在超过 {@link #EXPIRE_SECONDS} 秒自动消失。
 */
@Getter
public class DropItemUnit implements GameUnit {
    /** 拾取保护时间（秒） */
    public static final int PROTECT_SECONDS = 10;
    /** 掉落物过期时间（秒），超时自动清除 */
    public static final int EXPIRE_SECONDS = 300;

    private final String unitId;
    private final int itemId;
    private final int count;
    private int mapId;
    private final Position position = new Position();

    /** 创建时间戳（毫秒） */
    @Setter
    private long createTime;
    /** 拾取保护目标玩家ID（humanId），null 表示无保护 */
    @Setter
    private String protectorHumanId;

    public DropItemUnit(int itemId, int count) {
        this.unitId = String.valueOf(UnitUtils.genDropUnitId());
        this.itemId = itemId;
        this.count = count;
        this.createTime = System.currentTimeMillis();
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.DROP_ITEM;
    }

    @Override
    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    @Override
    public int getConfigId() {
        return itemId;
    }

    /** 掉落物无属性，返回 null */
    @Override
    public AttributeContainer getAttributeContainer() {
        return null;
    }

    /** 拾取保护是否已过期 */
    public boolean isProtectionExpired() {
        return System.currentTimeMillis() - createTime >= PROTECT_SECONDS * 1000L;
    }

    /** 掉落物是否已过期（应被清除） */
    public boolean isExpired() {
        return System.currentTimeMillis() - createTime >= EXPIRE_SECONDS * 1000L;
    }

    /** 指定玩家是否可以拾取此掉落物 */
    public boolean canPickup(String humanId) {
        if (protectorHumanId == null || isProtectionExpired()) {
            return true;
        }
        return protectorHumanId.equals(humanId);
    }

    @Override
    public String toString() {
        return "{unitId:" + unitId + ", itemId:" + itemId + ", count:" + count + "}";
    }
}
