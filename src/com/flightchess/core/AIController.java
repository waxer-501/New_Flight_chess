package com.flightchess.core;

import java.util.List;

/**
 * 简单 AI / 托管逻辑。
 *
 * 当前版本采用非常朴素的策略：
 * 1. 若有可以吃子的走法，优先选择；
 * 2. 否则，选择最靠前的棋子前进；
 * 3. 若只有起飞可用，则起飞。
 */
public class AIController {

    private final RuleEngine ruleEngine;

    public AIController(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    /**
     * 根据当前状态和骰子点数，为指定玩家选择一个棋子索引。
     * 若无可走棋子返回 -1。
     */
    public int choosePiece(GameState state, PlayerColor color, int dice) {
        List<Integer> movable = ruleEngine.listMovablePieces(state, color, dice);
        if (movable.isEmpty()) {
            return -1;
        }

        Player player = state.getPlayer(color);
        if (player == null) {
            return -1;
        }

        // 简化：直接选择外圈位置最大的那颗棋子（最靠前）。
        int bestIndex = -1;
        int bestPos = -1;
        for (int idx : movable) {
            Piece piece = player.getPieces().get(idx);
            if (piece.isInWaitingArea()) {
                // 起飞候选，暂存但优先级低于已有外圈棋子。
                if (bestIndex == -1) {
                    bestIndex = idx;
                }
            } else if (piece.getCellType() == CellType.OUTER) {
                int pos = piece.getPositionIndex();
                if (pos > bestPos) {
                    bestPos = pos;
                    bestIndex = idx;
                }
            }
        }

        return bestIndex;
    }
}

