package com.flightchess.core;

/**
 * 玩家颜色 / 阵营。
 */
public enum PlayerColor {
    GREEN,
    RED,
    BLUE,
    YELLOW;

    public static PlayerColor[] ordered() {
        return new PlayerColor[]{GREEN, RED, BLUE, YELLOW};
    }
}

