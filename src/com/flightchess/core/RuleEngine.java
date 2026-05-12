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
     * 等待区只有掷 6 可进入起飞格；起飞格任意 1~6 可出发；外圈任意正数可前进。
     */
    public boolean canMovePiece(GameState state, PlayerColor color, Piece piece, int dice) {
        if (state.isPlayerDead(color)) {
            return false;
        }
        if (piece.isDead()) {
            return false;
        }
        if (piece.isInWaitingArea()) {
            return dice >= 6; // >=6 以支持突然死亡模式双骰（可能得到 8/10/12）
        }
        if (piece.isInTakeoffArea()) {
            return dice >= 1 && dice <= 6;
        }

        return dice > 0;
    }

    /**
     * 获取某棋子在当前棋盘上的所有相邻可达位置（用于突然死亡模式步控）。
     * 每个返回元素为 int[]{ cellTypeOrdinal, positionIndex }。
     * 覆盖外圈、起飞格、终点通道、中心四格。
     */
    public List<int[]> getAdjacentPositions(GameState state, Piece piece) {
        List<int[]> result = new ArrayList<>();
        if (piece == null) return result;
        if (piece.getCellType() == CellType.OUTER) {
            int pos = piece.getPositionIndex();
            result.add(new int[]{ CellType.OUTER.ordinal(), BoardConfig.moveBackwardOnOuter(pos, 1) });
            result.add(new int[]{ CellType.OUTER.ordinal(), BoardConfig.moveForwardOnOuter(pos, 1) });
            // 若当前格是路口，加入终点通道入口
            if (BoardConfig.isJunction(pos)) {
                PlayerColor jColor = BoardConfig.getJunctionColor(pos);
                int cp = BoardConfig.encodeCenterPathPos(jColor, 0);
                if (cp >= 0) {
                    result.add(new int[]{ CellType.CENTER_PATH.ordinal(), cp });
                }
            }
        } else if (piece.getCellType() == CellType.TAKEOFF) {
            int startIdx = BoardConfig.getStartIndex(piece.getOwner());
            result.add(new int[]{ CellType.OUTER.ordinal(), startIdx });
        } else if (piece.getCellType() == CellType.CENTER_PATH) {
            int pos = piece.getPositionIndex();
            PlayerColor pathColor = BoardConfig.decodeCenterPathColor(pos);
            int step = BoardConfig.decodeCenterPathStep(pos);
            if (pathColor == null || step < 0) return result;
            // 外层方向
            int prev = BoardConfig.getFinishLanePrev(pathColor, step);
            if (prev >= 0) {
                result.add(new int[]{ CellType.CENTER_PATH.ordinal(),
                        BoardConfig.encodeCenterPathPos(pathColor, prev) });
            } else if (step == 0) {
                // 最外层 ←→ 外圈路口
                int junction = BoardConfig.getJunctionIndex(pathColor);
                if (junction >= 0) {
                    result.add(new int[]{ CellType.OUTER.ordinal(), junction });
                }
            }
            // 内层方向
            int next = BoardConfig.getFinishLaneNext(pathColor, step);
            if (next >= 0) {
                result.add(new int[]{ CellType.CENTER_PATH.ordinal(),
                        BoardConfig.encodeCenterPathPos(pathColor, next) });
            } else if (step == BoardConfig.FINISH_LANE_LENGTH - 1) {
                // 最内层 ←→ 中心格
                int ci = BoardConfig.colorToCenterIndex(pathColor);
                if (ci >= 0) {
                    result.add(new int[]{ CellType.CENTER.ordinal(), ci });
                }
            }
        } else if (piece.getCellType() == CellType.CENTER) {
            // 中心四格：四个方向分别通往四条终点通道的最内层
            for (PlayerColor pc : PlayerColor.ordered()) {
                int cp = BoardConfig.encodeCenterPathPos(pc, BoardConfig.FINISH_LANE_LENGTH - 1);
                if (cp >= 0) {
                    result.add(new int[]{ CellType.CENTER_PATH.ordinal(), cp });
                }
            }
        }
        return result;
    }

    /**
     * 突然死亡模式：将棋子向目标位置移动一步（不消耗骰子，由调用方递减 remainingSteps）。
     * 自动处理吃子。
     *
     * @return true 如果移动成功
     */
    /**
     * 突然死亡模式：将棋子向目标位置移动一步（不消耗骰子，由调用方递减 remainingSteps）。
     * 自动处理吃子。
     *
     * @return true 如果移动成功
     */
    public boolean moveOneStep(GameState state, PlayerColor color, int pieceIndex,
                                int targetCellTypeOrdinal, int targetPosition) {
        Player player = state.getPlayer(color);
        if (player == null) return false;
        if (pieceIndex < 0 || pieceIndex >= player.getPieces().size()) return false;
        Piece piece = player.getPieces().get(pieceIndex);

        // 检查目标位置是否相邻
        List<int[]> adjacent = getAdjacentPositions(state, piece);
        boolean valid = false;
        for (int[] adj : adjacent) {
            if (adj[0] == targetCellTypeOrdinal && adj[1] == targetPosition) {
                valid = true;
                break;
            }
        }
        if (!valid) return false;

        CellType targetType = CellType.values()[targetCellTypeOrdinal];

        // 吃子：目标位置若有敌方棋子则全部打回等待区
        List<Piece> enemiesAtTarget = new ArrayList<>();
        for (Player p : state.getPlayersInOrder()) {
            if (p.getColor() == color) continue;
            for (Piece enemy : p.getPieces()) {
                if (enemy.getCellType() == targetType && enemy.getPositionIndex() == targetPosition) {
                    enemiesAtTarget.add(enemy);
                }
            }
        }
        if (!enemiesAtTarget.isEmpty()) {
            for (Piece enemy : enemiesAtTarget) {
                enemy.setCellType(CellType.WAITING_AREA);
                enemy.setPositionIndex(-1);
            }
            if (enemiesAtTarget.size() >= 2) {
                piece.setCellType(CellType.WAITING_AREA);
                piece.setPositionIndex(-1);
                return true;
            }
        }

        piece.setCellType(targetType);
        piece.setPositionIndex(targetPosition);
        piece.setHasEverLeftWaitingArea(true);
        return true;
    }

    /**
     * 执行一次移动，返回是否获得额外回合（掷到 6 或吃子等）。
     *
     * - 等待区 + 掷 6：进入本方起飞格（TAKEOFF），可再掷一次；
     * - 起飞格 + 任意骰子：从起飞格出发，走 dice 步进入外圈（第 1 步到 startIndex，再走 dice-1 步），可吃子；
     * - 外圈：顺时针 dice 步，可吃子。
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
        boolean airRouted = false;
        int startIndex = BoardConfig.getStartIndex(color);

        if (piece.isInWaitingArea()) {
            // 摇到 6 并选择飞机：先进入起飞格，不直接上外圈
            piece.setCellType(CellType.TAKEOFF);
            piece.setPositionIndex(BoardConfig.getTakeoffIndex(color));
            piece.setHasEverLeftWaitingArea(true);
            extraTurn = true;
        } else if (piece.isInTakeoffArea()) {
            // 从起飞格出发：第 1 步到 startIndex，再走 dice-1 步
            int to = BoardConfig.moveForwardOnOuter(startIndex, dice - 1);
            // 先吃子（若落点有敌人），再判断航道和同色跳格
            Set<Piece> capturedPieces = getPiecesAtOuterIndex(state, to, color, false);
            if (!capturedPieces.isEmpty()) {
                captured = true;
                for (Piece enemy : capturedPieces) {
                    enemy.setCellType(CellType.DEAD);
                    enemy.setPositionIndex(-1);
                }
                if (capturedPieces.size() >= 2) {
                    piece.setCellType(CellType.DEAD);
                    piece.setPositionIndex(-1);
                    return MoveResult.success(true);
                }
            }
            // 航道入口跳转：落在外圈局部索引7且颜色匹配 → 跳到下一quarter局部索引6
            if (BoardConfig.isAirRouteEntry(to) && color == BoardConfig.getCellColorAtOuterIndex(to)) {
                to = BoardConfig.getAirRouteExit(to);
                airRouted = true;
                Set<Piece> airCaptured = getPiecesAtOuterIndex(state, to, color, false);
                if (!airCaptured.isEmpty()) {
                    captured = true;
                    for (Piece enemy : airCaptured) {
                        enemy.setCellType(CellType.DEAD);
                        enemy.setPositionIndex(-1);
                    }
                    if (airCaptured.size() >= 2) {
                        piece.setCellType(CellType.DEAD);
                        piece.setPositionIndex(-1);
                        return MoveResult.success(true);
                    }
                }
            }
            // 若落点格子颜色与棋子颜色相同，则再前进四格（航道跳转后不触发）
            if (!airRouted && color == BoardConfig.getCellColorAtOuterIndex(to)) {
                to = BoardConfig.moveForwardOnOuter(to, 4);
                // 跳后也检查是否有敌人
                Set<Piece> jumpCaptured = getPiecesAtOuterIndex(state, to, color, false);
                if (!jumpCaptured.isEmpty()) {
                    captured = true;
                    for (Piece enemy : jumpCaptured) {
                        enemy.setCellType(CellType.DEAD);
                        enemy.setPositionIndex(-1);
                    }
                    if (jumpCaptured.size() >= 2) {
                        piece.setCellType(CellType.DEAD);
                        piece.setPositionIndex(-1);
                        return MoveResult.success(true);
                    }
                }
            }
            piece.setCellType(CellType.OUTER);
            piece.setPositionIndex(to);
            if (dice == 6 || captured) {
                extraTurn = true;
            }
        } else if (piece.getCellType() == CellType.OUTER) {
            int from = piece.getPositionIndex();
            int to = BoardConfig.moveForwardOnOuter(from, dice);
            // 先吃子（若落点有敌人），再判断航道和同色跳格
            Set<Piece> capturedPieces = getPiecesAtOuterIndex(state, to, color, false);
            if (!capturedPieces.isEmpty()) {
                captured = true;
                for (Piece enemy : capturedPieces) {
                    enemy.setCellType(CellType.DEAD);
                    enemy.setPositionIndex(-1);
                }
                if (capturedPieces.size() >= 2) {
                    piece.setCellType(CellType.DEAD);
                    piece.setPositionIndex(-1);
                    return MoveResult.success(true);
                }
            }
            // 航道入口跳转：落在外圈局部索引7且颜色匹配 → 跳到下一quarter局部索引6
            if (BoardConfig.isAirRouteEntry(to) && color == BoardConfig.getCellColorAtOuterIndex(to)) {
                to = BoardConfig.getAirRouteExit(to);
                airRouted = true;
                Set<Piece> airCaptured = getPiecesAtOuterIndex(state, to, color, false);
                if (!airCaptured.isEmpty()) {
                    captured = true;
                    for (Piece enemy : airCaptured) {
                        enemy.setCellType(CellType.DEAD);
                        enemy.setPositionIndex(-1);
                    }
                    if (airCaptured.size() >= 2) {
                        piece.setCellType(CellType.DEAD);
                        piece.setPositionIndex(-1);
                        return MoveResult.success(true);
                    }
                }
            }
            // 若落点格子颜色与棋子颜色相同，则再前进四格（航道跳转后不触发）
            if (!airRouted && color == BoardConfig.getCellColorAtOuterIndex(to)) {
                to = BoardConfig.moveForwardOnOuter(to, 4);
                // 跳后也检查是否有敌人
                Set<Piece> jumpCaptured = getPiecesAtOuterIndex(state, to, color, false);
                if (!jumpCaptured.isEmpty()) {
                    captured = true;
                    for (Piece enemy : jumpCaptured) {
                        enemy.setCellType(CellType.DEAD);
                        enemy.setPositionIndex(-1);
                    }
                    if (jumpCaptured.size() >= 2) {
                        piece.setCellType(CellType.DEAD);
                        piece.setPositionIndex(-1);
                        return MoveResult.success(true);
                    }
                }
            }

            piece.setPositionIndex(to);

            if (dice == 6 || captured) {
                extraTurn = true;
            }
        }

        return MoveResult.success(extraTurn);
    }

    /**
     * 返回指定外圈格子上当前有几颗棋子（包含所有玩家）。
     *
     * @param state      当前状态
     * @param outerIndex 外圈索引
     * @return 该外圈格子上的棋子数量
     */
    public int getPieceCountAtOuterIndex(GameState state, int outerIndex) {
        if (state == null) {
            return 0;
        }
        int count = 0;
        for (Player p : state.getPlayersInOrder()) {
            for (Piece piece : p.getPieces()) {
                if (piece.getCellType() == CellType.OUTER && piece.getPositionIndex() == outerIndex) {
                    count++;
                }
            }
        }
        return count;
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
     * 复活玩家所有被吃掉的棋子：将其从隐藏状态移动到起飞格。
     * 若玩家没有隐藏棋子，调用无效果。
     *
     * @return 是否有棋子被复活
     */
    public boolean reviveDeadPieces(GameState state, PlayerColor color) {
        Player player = state.getPlayer(color);
        if (player == null) return false;

        int takeoffIndex = BoardConfig.getTakeoffIndex(color);
        boolean revived = false;
        for (Piece piece : player.getPieces()) {
            if (piece.isDead()) {
                piece.setCellType(CellType.TAKEOFF);
                piece.setPositionIndex(takeoffIndex);
                revived = true;
            }
        }
        return revived;
    }

    /**
     * 检查第四家补偿条件是否满足：
     * 其他三家都已至少起飞一架飞机，且本家尚未有任何飞机起飞。
     * 满足时，第四家每次掷骰后可额外摇一次直到摇到 6。
     */
    public boolean isFourthPlayerCompensationActive(GameState state, PlayerColor color) {
        Player player = state.getPlayer(color);
        if (player == null) return false;

        // 本家是否已起飞
        boolean selfLaunched = false;
        for (Piece piece : player.getPieces()) {
            if (piece.hasEverLeftWaitingArea()) {
                selfLaunched = true;
                break;
            }
        }
        if (selfLaunched) return false;

        // 检查其他三家是否都已起飞
        for (PlayerColor other : PlayerColor.ordered()) {
            if (other == color) continue;
            Player p = state.getPlayer(other);
            if (p == null) return false;
            boolean launched = false;
            for (Piece piece : p.getPieces()) {
                if (piece.hasEverLeftWaitingArea()) {
                    launched = true;
                    break;
                }
            }
            if (!launched) return false;
        }

        return true;
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

