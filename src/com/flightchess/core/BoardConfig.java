package com.flightchess.core;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 棋盘坐标与特殊格子定义。
 *
 * 外圈 52 格顺时针：0~12 下，13~25 左，26~38 上，39~51 右（与 GameBoardPanel 绘制一致）。
 * 出生点：左上红、右上蓝、右下黄、左下绿；起飞格为各角与外圈相接的那一格。
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

    // ===================== 终点通道拓扑（突然死亡模式） =====================

    /** 每方终点通道长度（格数）。 */
    public static final int FINISH_LANE_LENGTH = 5;

    /** 外圈路口索引 → 该路口通往的终点通道所属颜色。 */
    private static final Map<Integer, PlayerColor> JUNCTION_MAP = new HashMap<>();

    /** 每方终点通道所连接的外圈路口索引。 */
    private static final Map<PlayerColor, Integer> JUNCTION_INDEX = new EnumMap<>(PlayerColor.class);

    /** 中心格子数。 */
    public static final int CENTER_COUNT = 4;

    static {
        // 每方 13 格区间：绿 0~12(下)，红 13~25(左上)，蓝 26~38(上)，黄 39~51(右下)
        QUARTER_START.put(PlayerColor.GREEN, 0);
        QUARTER_START.put(PlayerColor.RED, 13);
        QUARTER_START.put(PlayerColor.BLUE, 26);
        QUARTER_START.put(PlayerColor.YELLOW, 39);

        // 出发到外圈的第一格 = 本方 1/4 起始格 + START_OFFSET_FROM_QUARTER
        for (PlayerColor color : PlayerColor.ordered()) {
            int quarterStart = QUARTER_START.get(color);
            START_INDEX.put(color, (quarterStart + START_OFFSET_FROM_QUARTER) % OUTER_CELL_COUNT);
        }

        // 逻辑起飞格索引（非外圈）：红=-1，蓝=-2，黄=-3，绿=-4
        TAKEOFF_INDEX.put(PlayerColor.RED, -1);
        TAKEOFF_INDEX.put(PlayerColor.BLUE, -2);
        TAKEOFF_INDEX.put(PlayerColor.YELLOW, -3);
        TAKEOFF_INDEX.put(PlayerColor.GREEN, -4);

        for (PlayerColor color : PlayerColor.ordered()) {
            int start = QUARTER_START.get(color);
            Set<Integer> zone = new HashSet<>();
            for (int i = 0; i < QUARTER_LENGTH; i++) {
                zone.add((start + i) % OUTER_CELL_COUNT);
            }
            DOUBLE_ZONE.put(color, zone);
        }

        // 终点通道路口：每方 quarter 起始格即路口（与描绘终点通道的 base 位置对齐）
        JUNCTION_INDEX.put(PlayerColor.GREEN, 0);
        JUNCTION_INDEX.put(PlayerColor.RED, 13);
        JUNCTION_INDEX.put(PlayerColor.BLUE, 26);
        JUNCTION_INDEX.put(PlayerColor.YELLOW, 39);
        for (Map.Entry<PlayerColor, Integer> e : JUNCTION_INDEX.entrySet()) {
            JUNCTION_MAP.put(e.getValue(), e.getKey());
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

    // ===================== 终点通道 / 中心拓扑 =====================

    /** 外圈索引是否为某个终点通道路口。 */
    public static boolean isJunction(int outerIndex) {
        return JUNCTION_MAP.containsKey(outerIndex);
    }

    /** 获取该外圈路口通往的终点通道颜色，非路口返回 null。 */
    public static PlayerColor getJunctionColor(int outerIndex) {
        return JUNCTION_MAP.get(outerIndex);
    }

    /** 获取某方终点通道所连接的外圈路口索引。 */
    public static int getJunctionIndex(PlayerColor color) {
        return JUNCTION_INDEX.getOrDefault(color, -1);
    }

    // ===================== CENTER_PATH 位置编码 =====================
    // positionIndex = colorOrdinal * 5 + laneStep (0~4)
    // GREEN=0~4, RED=5~9, BLUE=10~14, YELLOW=15~19

    public static int encodeCenterPathPos(PlayerColor color, int laneStep) {
        int colorOrd = colorToCenterIndex(color);
        if (colorOrd < 0 || laneStep < 0 || laneStep >= FINISH_LANE_LENGTH) return -1;
        return colorOrd * FINISH_LANE_LENGTH + laneStep;
    }

    public static PlayerColor decodeCenterPathColor(int pos) {
        if (pos < 0 || pos >= CENTER_COUNT * FINISH_LANE_LENGTH) return null;
        return centerIndexToColor(pos / FINISH_LANE_LENGTH);
    }

    public static int decodeCenterPathStep(int pos) {
        if (pos < 0 || pos >= CENTER_COUNT * FINISH_LANE_LENGTH) return -1;
        return pos % FINISH_LANE_LENGTH;
    }

    /** 获取某方终点通道中相邻更外层的格子索引（外=0, 内=4），超出范围返回 -1。 */
    public static int getFinishLanePrev(PlayerColor color, int laneIndex) {
        if (laneIndex <= 0 || laneIndex >= FINISH_LANE_LENGTH) return -1;
        return laneIndex - 1;
    }

    /** 获取某方终点通道中相邻更内层的格子索引，超出范围返回 -1。 */
    public static int getFinishLaneNext(PlayerColor color, int laneIndex) {
        if (laneIndex < 0 || laneIndex >= FINISH_LANE_LENGTH - 1) return -1;
        return laneIndex + 1;
    }

    /** 获取中心格顺时针下一格索引 (0=GREEN, 1=RED, 2=BLUE, 3=YELLOW)。 */
    public static int getCenterNext(int centerIndex) {
        return (centerIndex + 1) % CENTER_COUNT;
    }

    /** 获取中心格逆时针上一格索引。 */
    public static int getCenterPrev(int centerIndex) {
        return (centerIndex - 1 + CENTER_COUNT) % CENTER_COUNT;
    }

    /** 颜色 → 中心格索引 (GREEN=0, RED=1, BLUE=2, YELLOW=3)。 */
    public static int colorToCenterIndex(PlayerColor color) {
        PlayerColor[] order = PlayerColor.ordered();
        for (int i = 0; i < order.length; i++) {
            if (order[i] == color) return i;
        }
        return -1;
    }

    /** 中心格索引 → 颜色。 */
    public static PlayerColor centerIndexToColor(int idx) {
        PlayerColor[] order = PlayerColor.ordered();
        if (idx >= 0 && idx < order.length) return order[idx];
        return null;
    }

    /** 判断某外圈索引是否处于指定玩家的 1/4 段内。 */
    /**
     * 判断某外圈索引是否为航道入口（局部索引 7，即 BOTTOM_LEFT 三角形）。
     */
    public static boolean isAirRouteEntry(int outerIndex) {
        if (outerIndex < 0 || outerIndex >= OUTER_CELL_COUNT) {
            return false;
        }
        return outerIndex % QUARTER_LENGTH == 7;
    }

    /**
     * 根据航道入口索引，计算出口索引（顺时针下一个 quarter 的局部索引 6）。
     */
    public static int getAirRouteExit(int entryIndex) {
        int quarter = entryIndex / QUARTER_LENGTH;
        int nextQuarter = (quarter + 1) % 4;
        return nextQuarter * QUARTER_LENGTH + 6;
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

