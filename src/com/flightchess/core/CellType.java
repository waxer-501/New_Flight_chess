package com.flightchess.core;

/**
 * 棋盘格子类型。
 */
public enum CellType {
    OUTER,          // 外圈主路径
    TAKEOFF,        // 起飞格（摇到 6 选飞机后先进入，再摇骰子从此格出发）
    COLORED,        // 本方颜色格
    AIR_ROUTE,      // 航道格（飞行路径）
    CENTER_PATH,    // 通向中心的纯色路径
    CENTER,         // 中心四格
    WAITING_AREA,   // 等待区（初始未起飞棋子）
    DEAD            // 被吃后隐藏，等待连续两次 6 复活
}

