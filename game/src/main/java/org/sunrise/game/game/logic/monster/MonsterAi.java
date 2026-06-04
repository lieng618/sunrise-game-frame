package org.sunrise.game.game.logic.monster;

import org.sunrise.game.game.config.Enum.AttributeType;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.map.MapNavData;
import org.sunrise.game.game.logic.map.MapNavUtils;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.MonsterUnit;
import org.sunrise.game.game.logic.unit.Position;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 怪物 AI，负责巡逻移动等行为逻辑。
 *
 * <p>生命周期与 {@link MonsterUnit} 绑定：刷怪时由 {@link MonsterSpawner} 创建并挂到单位上，
 * 怪物死亡后随单位一起被丢弃，复活时会重新创建。
 *
 * <p>驱动方式：不自行开定时器，而是由所在地图的 {@link GameMap#pulsePer100Ms()} 每 100ms 调用
 * {@link #pulsePer100Ms(long)}。调用链为
 * {@code MapSystem.pulsePer100Ms → GameMap.pulsePer100Ms → MonsterAi.pulsePer100Ms}。
 *
 * <p>位置同步：AI 在创建时保存 {@link #mapId}，位置变更后通过 mapId 查找 {@link GameMap}，
 * 调用 {@link GameMap#broadcastUnitPosition} 向当前地图玩家广播。暂不考虑地图销毁场景。
 *
 * <p>当前已实现：出生点周围随机巡逻；后续可在此类扩展追击、脱战回位、技能释放等。
 */
public class MonsterAi {

    /** 巡逻半径：以出生点为圆心，在此范围内随机选目标点 */
    private static final float PATROL_RADIUS = 120f;

    /** 到达判定距离：与目标点距离小于等于此值视为到达 */
    private static final float ARRIVAL_THRESHOLD = 4f;

    /** 到达目标后的站立时间（毫秒） */
    private static final long PATROL_IDLE_MS = 2000L;

    /** 随机选巡逻目标时的最大尝试次数，全部失败则回到出生点 */
    private static final int PICK_TARGET_ATTEMPTS = 24;

    /** 绑定的怪物单位，读写位置与属性均通过此对象 */
    private final MonsterUnit unit;

    /** 所在地图 ID，创建时写入，用于查找地图并广播位置 */
    private final int mapId;

    /** 巡逻锚点 X（通常为配置表出生点） */
    private float patrolAnchorX;

    /** 巡逻锚点 Y（通常为配置表出生点） */
    private float patrolAnchorY;

    /** 当前巡逻目标 X */
    private float targetX;

    /** 当前巡逻目标 Y */
    private float targetY;

    /** 站立结束时间戳（毫秒）；{@code nowMs < idleUntilMs} 表示仍在站立 */
    private long idleUntilMs;

    /** 上次向客户端广播位置的时间戳，用于节流 */
    private long lastBroadcastMs;

    /** 本帧解析后的实际落脚点，避免频繁分配 */
    private final float[] stepBuffer = new float[2];

    /** 按速度推算的下一帧理想位置，避免频繁分配 */
    private final float[] nextBuffer = new float[2];

    /**
     * 创建怪物 AI 并初始化第一轮巡逻目标。
     *
     * @param unit           绑定的怪物单位
     * @param mapId          所在地图 ID
     * @param patrolAnchorX  巡逻锚点 X（出生点）
     * @param patrolAnchorY  巡逻锚点 Y（出生点）
     */
    public MonsterAi(MonsterUnit unit, int mapId, float patrolAnchorX, float patrolAnchorY) {
        this.unit = unit;
        this.mapId = mapId;
        this.patrolAnchorX = patrolAnchorX;
        this.patrolAnchorY = patrolAnchorY;
        pickPatrolTarget(null);
    }

    /**
     * 100ms 心跳入口，由 {@link GameMap#pulsePer100Ms()} 调用。
     *
     * <p>单帧流程：站立中则跳过 → 已到达则进入站立并换目标 → 否则按速度向目标移动一步，
     * 遇阻挡或无法前进则重新选目标；位置变化后按需广播。
     *
     * @param nowMs 当前时间戳（毫秒）
     */
    public void pulsePer100Ms(long nowMs) {
        if (!unit.isAlive()) {
            return;
        }
        if (isIdling(nowMs)) {
            return;
        }

        MapNavData navData = MapNavUtils.get(mapId);
        Position pos = unit.getPosition();
        if (hasArrived(pos)) {
            startIdle(nowMs);
            pickPatrolTarget(navData);
            broadcastPositionIfNeeded(nowMs);
            return;
        }

        double speed = unit.getAttributeContainer().getFinalValue(AttributeType.SPEED);
        if (speed <= 0) {
            return;
        }

        computeNextPosition(pos, speed, nextBuffer);
        if (!resolveStep(navData, pos.getX(), pos.getY(),
                nextBuffer[0], nextBuffer[1], stepBuffer)) {
            pickPatrolTarget(navData);
            return;
        }

        float nextX = stepBuffer[0];
        float nextY = stepBuffer[1];
        if (nextX == pos.getX() && nextY == pos.getY()) {
            pickPatrolTarget(navData);
            return;
        }

        float orientation = (float) Math.atan2(nextY - pos.getY(), nextX - pos.getX());
        pos.set(nextX, nextY, pos.getZ(), orientation);
        broadcastPositionIfNeeded(nowMs);
    }

    /**
     * 判断当前位置是否已到达巡逻目标。
     */
    private boolean hasArrived(Position pos) {
        float dx = targetX - pos.getX();
        float dy = targetY - pos.getY();
        return dx * dx + dy * dy <= ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD;
    }

    /**
     * 按速度与 100ms 帧长，计算朝目标方向迈一步后的理想坐标。
     *
     * @param pos   当前位置
     * @param speed 移动速度（单位/秒）
     * @param out   长度为 2 的输出数组，写入 [x, y]
     */
    private void computeNextPosition(Position pos, double speed, float[] out) {
        float dx = targetX - pos.getX();
        float dy = targetY - pos.getY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float step = (float) (speed * ToolsUtils.PULSE_100MS_SEC);

        if (step >= dist) {
            out[0] = targetX;
            out[1] = targetY;
        } else {
            out[0] = pos.getX() + dx / dist * step;
            out[1] = pos.getY() + dy / dist * step;
        }
    }

    /**
     * 从当前位置向目标迈一步，遇阻挡时尝试沿 X 轴或 Y 轴单轴滑动。
     *
     * <p>优先级：直达目标 → 仅改 X → 仅改 Y；三者均不可走则返回 false。
     *
     * @param navData 地图导航数据，可为 null（视为全部可走）
     * @param fromX   起点 X
     * @param fromY   起点 Y
     * @param toX     理想终点 X
     * @param toY     理想终点 Y
     * @param out     长度为 2 的输出数组，成功时写入实际落脚点 [x, y]
     * @return 是否找到可行走的下一点
     */
    private boolean resolveStep(
            MapNavData navData,
            float fromX, float fromY,
            float toX, float toY,
            float[] out) {
        if (isWalkable(navData, toX, toY)) {
            out[0] = toX;
            out[1] = toY;
            return true;
        }
        if (isWalkable(navData, toX, fromY)) {
            out[0] = toX;
            out[1] = fromY;
            return true;
        }
        if (isWalkable(navData, fromX, toY)) {
            out[0] = fromX;
            out[1] = toY;
            return true;
        }
        return false;
    }

    /**
     * 判断坐标是否可走。无导航数据时默认全部可走。
     */
    private boolean isWalkable(MapNavData navData, float x, float y) {
        return navData == null || navData.isWalkable(x, y);
    }

    /**
     * 在巡逻锚点周围随机选一个可走的目标点。
     *
     * <p>在 {@link #PATROL_RADIUS} 范围内随机角度与半径采样；多次失败后退回锚点本身。
     *
     * @param navData 地图导航数据，可为 null
     */
    private void pickPatrolTarget(MapNavData navData) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < PICK_TARGET_ATTEMPTS; i++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float radius = random.nextFloat() * PATROL_RADIUS;
            float x = patrolAnchorX + (float) Math.cos(angle) * radius;
            float y = patrolAnchorY + (float) Math.sin(angle) * radius;
            if (isWalkable(navData, x, y)) {
                targetX = x;
                targetY = y;
                return;
            }
        }
        targetX = patrolAnchorX;
        targetY = patrolAnchorY;
    }

    /** 到达目标后进入站立状态 */
    private void startIdle(long nowMs) {
        idleUntilMs = nowMs + PATROL_IDLE_MS;
    }

    /** 是否仍在站立等待中 */
    private boolean isIdling(long nowMs) {
        return nowMs < idleUntilMs;
    }

    /**
     * 按 100ms 间隔节流广播位置，避免每帧都发包。
     */
    private void broadcastPositionIfNeeded(long nowMs) {
        if (nowMs - lastBroadcastMs < ToolsUtils.PULSE_100MS_MILLIS) {
            return;
        }
        lastBroadcastMs = nowMs;
        broadcastPosition();
    }

    /**
     * 根据创建时保存的 mapId 查找地图，向地图内玩家广播单位位置。
     */
    private void broadcastPosition() {
        GameMap gameMap = GameSystemUtils.getSystem(MapSystem.class).getMap(mapId);
        if (gameMap != null) {
            gameMap.broadcastUnitPosition(unit);
        }
    }
}
