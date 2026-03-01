package com.flightchess.core;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 棋盘坐标与特殊格子定义。
 *
 * 外圈 52 格顺时针：0~12 下，13~25 左，26~38 上，39~51 右（与 GameBoardPanel 绘制一致）。
 * 出生点：左上黄、右上蓝、右下红、左下绿；起飞格为各角与外圈相接的那一格。
 */
public final class BoardConfig {

    private BoardConfig() {
    }

    /** 外圈格子总数。 */
    public static final int OUTER_CELL_COUNT = 52;

    /** 每方外圈 1/4 段长度。 */
    public static final int QUARTER_LENGTH = OUTER_CELL_COUNT / 4; // 13

    /** 每方“出发到外圈”的起始索引（= 该方 1/4 外圈起始格 + 3，即起飞后第一步落点）。 */
    private static final Map<PlayerColor, Integer> START_INDEX = new EnumMap<>(PlayerColor.class);
    /** 每方“起飞格”逻辑索引（独立于外圈，使用负值避免冲突）。 */
    private static final Map<PlayerColor, Integer> TAKEOFF_INDEX = new EnumMap<>(PlayerColor.class);

    /** 每方所属外圈区间起始索引（含起飞格的那一段 13 格）。 */
    private static final Map<PlayerColor, Integer> QUARTER_START = new EnumMap<>(PlayerColor.class);

    /** 双倍区（死亡后激活）映射：玩家 -> 该玩家死亡时激活的外圈格集合。 */
    private static final Map<PlayerColor, Set<Integer>> DOUBLE_ZONE = new EnumMap<>(PlayerColor.class);

    /** 起飞后第一步落在本方 1/4 段的第几格（0=起始格，3=起始格+3）。 */
    private static final int START_OFFSET_FROM_QUARTER = 3;

    static {
        // 每方 13 格区间：绿 0~12(下)，红 39~51(右)，蓝 26~38(上)，黄 13~25(左)
        QUARTER_START.put(PlayerColor.GREEN, 0);
        QUARTER_START.put(PlayerColor.RED, 39);
        QUARTER_START.put(PlayerColor.BLUE, 26);
        QUARTER_START.put(PlayerColor.YELLOW, 13);

        // 出发到外圈的第一格 = 本方 1/4 起始格 + START_OFFSET_FROM_QUARTER
        for (PlayerColor color : PlayerColor.ordered()) {
            int quarterStart = QUARTER_START.get(color);
            START_INDEX.put(color, (quarterStart + START_OFFSET_FROM_QUARTER) % OUTER_CELL_COUNT);
        }

        // 逻辑起飞格索引（非外圈）：黄=-1，蓝=-2，红=-3，绿=-4
        TAKEOFF_INDEX.put(PlayerColor.YELLOW, -1);
        TAKEOFF_INDEX.put(PlayerColor.BLUE, -2);
        TAKEOFF_INDEX.put(PlayerColor.RED, -3);
        TAKEOFF_INDEX.put(PlayerColor.GREEN, -4);

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

    public static int getTakeoffIndex(PlayerColor color) {
        return TAKEOFF_INDEX.get(color);
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
     * 获取外圈某格子的颜色（与 GameBoardPanel 绘制一致：0~51 格按 GREEN,RED,BLUE,YELLOW 循环）。
     * 用于规则「落点与棋子同色则再前进四格」。
     */
    public static PlayerColor getCellColorAtOuterIndex(int outerIndex) {
        if (outerIndex < 0 || outerIndex >= OUTER_CELL_COUNT) {
            return null;
        }
        PlayerColor[] order = PlayerColor.ordered();
        return order[outerIndex % 4];
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

