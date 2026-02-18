package com.flightchess.core;

/**
 * 棋盘格子类型。
 */
public enum CellType {
    OUTER,          // 外圈主路径
    COLORED,        // 本方颜色格
    AIR_ROUTE,      // 航道格（飞行路径）
    CENTER_PATH,    // 通向中心的纯色路径
    CENTER,         // 中心四格
    WAITING_AREA    // 等待复活区（逻辑区域，不一定是真实格子）
}

