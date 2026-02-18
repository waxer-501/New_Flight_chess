package com.flightchess.core;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 棋盘坐标与特殊格子定义。
 *
 * 为了便于实现，先以常量表的形式固化：
 * - 外圈 52 格：索引 0~51，顺时针。
 * - 每方起飞格：GREEN=0, RED=13, BLUE=26, YELLOW=39。
 * - 每方外圈区间：各占 13 格。
 *
 * 关于颜色格、航道等，更复杂的位置可后续通过常量数组补充。
 */
public final class BoardConfig {

    private BoardConfig() {
    }

    /** 外圈格子总数。 */
    public static final int OUTER_CELL_COUNT = 52;

    /** 每方外圈 1/4 段长度。 */
    public static final int QUARTER_LENGTH = OUTER_CELL_COUNT / 4; // 13

    /** 每方起飞格索引。 */
    private static final Map<PlayerColor, Integer> START_INDEX = new EnumMap<>(PlayerColor.class);

    /** 每方所属外圈区间起始索引。 */
    private static final Map<PlayerColor, Integer> QUARTER_START = new EnumMap<>(PlayerColor.class);

    /** 双倍区（死亡后激活）映射：玩家 -> 该玩家死亡时激活的外圈格集合。 */
    private static final Map<PlayerColor, Set<Integer>> DOUBLE_ZONE = new EnumMap<>(PlayerColor.class);

    static {
        START_INDEX.put(PlayerColor.GREEN, 0);
        START_INDEX.put(PlayerColor.RED, 13);
        START_INDEX.put(PlayerColor.BLUE, 26);
        START_INDEX.put(PlayerColor.YELLOW, 39);

        QUARTER_START.put(PlayerColor.GREEN, 0);
        QUARTER_START.put(PlayerColor.RED, 13);
        QUARTER_START.put(PlayerColor.BLUE, 26);
        QUARTER_START.put(PlayerColor.YELLOW, 39);

        for (PlayerColor color : PlayerColor.ordered()) {
            int start = QUARTER_START.get(color);
            Set<Integer> zone = new HashSet<>();
            for (int i = 0; i < QUARTER_LENGTH; i++) {
                zone.add((start + i) % OUTER_CELL_COUNT);
            }
            DOUBLE_ZONE.put(color, zone);
        }
    }

    public static int getStartIndex(PlayerColor color) {
        return START_INDEX.get(color);
    }

    public static int getQuarterStart(PlayerColor color) {
        return QUARTER_START.get(color);
    }

    /**
     * 取得该玩家死亡后激活的双倍区外圈格集合。
     */
    public static Set<Integer> getDoubleZoneFor(PlayerColor color) {
        return DOUBLE_ZONE.get(color);
    }

    /**
     * 计算从某个外圈索引向前走 step 步后落在哪个索引（正常模式下仅顺时针）。
     */
    public static int moveForwardOnOuter(int fromIndex, int step) {
        int n = OUTER_CELL_COUNT;
        int result = (fromIndex + step) % n;
        if (result < 0) {
            result += n;
        }
        return result;
    }

    /**
     * 计算从某个外圈索引向后走 step 步后落在哪个索引（突然死亡模式下允许逆时针）。
     */
    public static int moveBackwardOnOuter(int fromIndex, int step) {
        return moveForwardOnOuter(fromIndex, -step);
    }

    /**
     * 判断某外圈索引是否处于指定玩家的 1/4 段内。
     */
    public static boolean isInQuarter(PlayerColor color, int outerIndex) {
        int start = QUARTER_START.get(color);
        int endExclusive = (start + QUARTER_LENGTH) % OUTER_CELL_COUNT;
        if (start < endExclusive) {
            return outerIndex >= start && outerIndex < endExclusive;
        } else {
            // 环形区间跨越了末尾
            return outerIndex >= start || outerIndex < endExclusive;
        }
    }
}

