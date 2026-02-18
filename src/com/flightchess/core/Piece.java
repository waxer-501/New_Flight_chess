package com.flightchess.core;

import java.io.Serializable;

/**
 * 单个棋子状态。
 */
public class Piece implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PlayerColor owner;

    /**
     * 所在区域类型：
     * - OUTER: 外圈，positionIndex 为 0~51
     * - CENTER_PATH / CENTER: 突然死亡模式下使用的内部路径
     * - WAITING_AREA: 等待复活区
     */
    private CellType cellType;

    /**
     * 具体格子索引，含义随 cellType 不同：
     * - OUTER: 外圈索引
     * - CENTER_PATH / CENTER: 内部路径/中心索引
     * - WAITING_AREA: -1
     */
    private int positionIndex;

    public Piece(PlayerColor owner) {
        this.owner = owner;
        this.cellType = CellType.WAITING_AREA;
        this.positionIndex = -1;
    }

    public PlayerColor getOwner() {
        return owner;
    }

    public CellType getCellType() {
        return cellType;
    }

    public void setCellType(CellType cellType) {
        this.cellType = cellType;
    }

    public int getPositionIndex() {
        return positionIndex;
    }

    public void setPositionIndex(int positionIndex) {
        this.positionIndex = positionIndex;
    }

    public boolean isInWaitingArea() {
        return cellType == CellType.WAITING_AREA;
    }
}

