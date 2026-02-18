package com.flightchess.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 规则引擎：实现 PRD 中的大部分规则。
 *
 * 该类不做任何网络 / UI 相关操作，只处理纯粹的状态转换，
 * 方便在房主与客户端之间通过 GameState 快照同步。
 */
public class RuleEngine {

    private final Random random = new Random();

    /**
     * 掷骰，返回 1~6。
     * 由房主调用，然后广播结果。
     */
    public int rollDice() {
        return random.nextInt(6) + 1;
    }

    /**
     * 计算当前玩家在给定骰子值下所有可走的棋子索引列表。
     * 这里只返回“哪颗棋子可以动”，具体路径由 UI 结合 BoardConfig 计算/展示。
     */
    public List<Integer> listMovablePieces(GameState state, PlayerColor color, int dice) {
        List<Integer> result = new ArrayList<>();
        Player player = state.getPlayer(color);
        if (player == null) {
            return result;
        }

        for (int i = 0; i < player.getPieces().size(); i++) {
            Piece piece = player.getPieces().get(i);
            if (canMovePiece(state, color, piece, dice)) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * 判断某颗棋子在给定骰子值下是否可以移动。
     * 目前采用简化逻辑：等待复活区只能在掷出 6 时起飞，其余在外圈上的棋子总是可以向前行进。
     */
    public boolean canMovePiece(GameState state, PlayerColor color, Piece piece, int dice) {
        if (state.isPlayerDead(color)) {
            return false;
        }

        if (piece.isInWaitingArea()) {
            // 等待复活区 / 基地：只有掷出 6 才能起飞。
            return dice == 6;
        }

        // 外圈与突然死亡模式下的更复杂规则，可进一步细化。
        return dice > 0;
    }

    /**
     * 执行一次移动，返回是否获得额外回合（掷到 6 或吃子等）。
     *
     * 为了简化，该方法只处理：
     * - 起飞：从等待复活区到本方起飞格；
     * - 外圈移动：顺时针前进 dice 步；
     * - 吃子：若落点有敌方棋子，则送入等待复活区；
     * - 连续两次 6 复活：计数逻辑交由上层根据骰子结果处理，这里只提供一个工具方法。
     */
    public MoveResult movePiece(GameState state, PlayerColor color, int pieceIndex, int dice) {
        Player player = state.getPlayer(color);
        if (player == null) {
            return MoveResult.invalid("玩家不存在");
        }
        if (pieceIndex < 0 || pieceIndex >= player.getPieces().size()) {
            return MoveResult.invalid("棋子索引非法");
        }

        Piece piece = player.getPieces().get(pieceIndex);
        if (!canMovePiece(state, color, piece, dice)) {
            return MoveResult.invalid("该棋子在当前骰子值下不可移动");
        }

        boolean extraTurn = false;
        boolean captured = false;

        if (piece.isInWaitingArea()) {
            // 起飞
            int startIndex = BoardConfig.getStartIndex(color);
            piece.setCellType(CellType.OUTER);
            piece.setPositionIndex(startIndex);
            // 起飞后根据规则可以再掷一次。
            extraTurn = true;
        } else if (piece.getCellType() == CellType.OUTER) {
            int from = piece.getPositionIndex();
            int to = BoardConfig.moveForwardOnOuter(from, dice);

            // TODO: 这里未来可以插入颜色格跳跃 / 航道 / 双倍区等更复杂逻辑。

            // 先处理吃子：检查落点是否有敌方棋子。
            Set<Piece> capturedPieces = getPiecesAtOuterIndex(state, to, color, false);
            if (!capturedPieces.isEmpty()) {
                captured = true;
                for (Piece enemy : capturedPieces) {
                    enemy.setCellType(CellType.WAITING_AREA);
                    enemy.setPositionIndex(-1);
                }
            }

            piece.setPositionIndex(to);

            // 掷到 6 或吃子都可以再掷一次。
            if (dice == 6 || captured) {
                extraTurn = true;
            }
        }

        // 检查是否有人死亡 / 触发突然死亡模式，由上层根据 GameState.getActivePieceCount() 等进行判断。
        return MoveResult.success(extraTurn);
    }

    /**
     * 获取指定外圈索引上的棋子集合。
     *
     * @param state         当前状态
     * @param outerIndex    外圈索引
     * @param color         视角玩家颜色
     * @param includeSelf   是否包含自己的棋子
     */
    private Set<Piece> getPiecesAtOuterIndex(GameState state,
                                             int outerIndex,
                                             PlayerColor color,
                                             boolean includeSelf) {
        Set<Piece> result = new HashSet<>();
        for (Player p : state.getPlayersInOrder()) {
            if (!includeSelf && p.getColor() == color) {
                continue;
            }
            for (Piece piece : p.getPieces()) {
                if (piece.getCellType() == CellType.OUTER && piece.getPositionIndex() == outerIndex) {
                    result.add(piece);
                }
            }
        }
        return result;
    }

    /**
     * 简单结果封装，便于 UI / 网络层使用。
     */
    public static class MoveResult {
        private final boolean success;
        private final boolean extraTurn;
        private final String errorMessage;

        private MoveResult(boolean success, boolean extraTurn, String errorMessage) {
            this.success = success;
            this.extraTurn = extraTurn;
            this.errorMessage = errorMessage;
        }

        public static MoveResult success(boolean extraTurn) {
            return new MoveResult(true, extraTurn, null);
        }

        public static MoveResult invalid(String message) {
            return new MoveResult(false, false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isExtraTurn() {
            return extraTurn;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

