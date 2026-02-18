package com.flightchess.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 整个对局的状态快照。
 *
 * 该类不关心网络与 UI，仅包含规则相关信息，便于在服务端和客户端之间序列化传输。
 */
public class GameState implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int OUTER_CELL_COUNT = 52;
    public static final int PIECES_PER_PLAYER = 4;

    private final Map<PlayerColor, Player> players = new EnumMap<>(PlayerColor.class);

    /** 当前回合轮到的玩家颜色，按 GREEN -> RED -> BLUE -> YELLOW 顺序循环。 */
    private PlayerColor currentTurn;

    /** 当前对局阶段：正常 / 突然死亡。 */
    private GamePhase phase = GamePhase.NORMAL;

    /** 记录每个玩家连续掷出 6 的次数，用于“连续两次 6 复活”规则。 */
    private final Map<PlayerColor, Integer> consecutiveSixCount = new EnumMap<>(PlayerColor.class);

    /** 记录当前已死亡的玩家，用于触发双倍区与突然死亡模式。 */
    private final Map<PlayerColor, Boolean> deadPlayers = new EnumMap<>(PlayerColor.class);

    /** 对局唯一 ID，方便日志与重连校验。 */
    private final String gameId = UUID.randomUUID().toString();

    public GameState(List<String> nicknames) {
        PlayerColor[] colors = PlayerColor.ordered();
        for (int i = 0; i < colors.length; i++) {
            String nickname = (i < nicknames.size()) ? nicknames.get(i) : ("Player" + (i + 1));
            PlayerColor color = colors[i];
            Player player = new Player(color.name(), nickname, color, PIECES_PER_PLAYER);
            players.put(color, player);
            consecutiveSixCount.put(color, 0);
            deadPlayers.put(color, false);
        }
        currentTurn = PlayerColor.GREEN;
    }

    public Map<PlayerColor, Player> getPlayers() {
        return players;
    }

    public Player getPlayer(PlayerColor color) {
        return players.get(color);
    }

    public PlayerColor getCurrentTurn() {
        return currentTurn;
    }

    public void setCurrentTurn(PlayerColor currentTurn) {
        this.currentTurn = currentTurn;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int getConsecutiveSixCount(PlayerColor color) {
        Integer v = consecutiveSixCount.get(color);
        return v == null ? 0 : v;
    }

    public void setConsecutiveSixCount(PlayerColor color, int count) {
        consecutiveSixCount.put(color, count);
    }

    public boolean isPlayerDead(PlayerColor color) {
        Boolean v = deadPlayers.get(color);
        return v != null && v;
    }

    public void setPlayerDead(PlayerColor color, boolean dead) {
        deadPlayers.put(color, dead);
    }

    public String getGameId() {
        return gameId;
    }

    /**
     * 计算当前存活玩家数量。
     */
    public int getAlivePlayerCount() {
        int count = 0;
        for (PlayerColor color : PlayerColor.ordered()) {
            if (!isPlayerDead(color)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 返回当前场上仍有棋子的总数（不含等待复活区的棋子）。
     * 该值在判断“只剩四枚棋子”时有用。
     */
    public int getActivePieceCount() {
        int total = 0;
        for (Player player : players.values()) {
            for (Piece piece : player.getPieces()) {
                if (!piece.isInWaitingArea()) {
                    total++;
                }
            }
        }
        return total;
    }

    /**
     * 获取所有玩家的列表，按颜色顺序返回。
     */
    public List<Player> getPlayersInOrder() {
        List<Player> list = new ArrayList<>();
        for (PlayerColor color : PlayerColor.ordered()) {
            Player p = players.get(color);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }
}

