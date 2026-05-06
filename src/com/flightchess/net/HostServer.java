package com.flightchess.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.flightchess.core.AIController;
import com.flightchess.core.GamePhase;
import com.flightchess.core.GameState;
import com.flightchess.core.Piece;
import com.flightchess.core.Player;
import com.flightchess.core.PlayerColor;
import com.flightchess.core.PlayerState;
import com.flightchess.core.RuleEngine;
import com.flightchess.core.RuleEngine.MoveResult;

/**
 * 房主端服务器：单房间实现。
 *
 * - 房主本机启动一个 ServerSocket；
 * - 其他玩家通过 TCP 连接加入；
 * - 所有规则裁决都在此处完成，并通过广播同步 GameState。
 */
public class HostServer {

    private final int port;
    private final String roomId;

    private final RuleEngine ruleEngine = new RuleEngine();
    private final AIController aiController = new AIController(ruleEngine);
    private GameState gameState;
    private RoomInfo roomInfo;

    private final Map<String, ClientHandler> clients = Collections.synchronizedMap(new HashMap<>());
    /** playerId -> 颜色 */
    private final Map<String, PlayerColor> playerColors = Collections.synchronizedMap(new HashMap<>());
    /** 已经被分配过的颜色集合 */
    private final Set<PlayerColor> usedColors = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 掷骰后等待该玩家选择棋子：点数与掷骰者 id，选子并移动后清零。 */
    private volatile Integer pendingDiceResult = null;
    private volatile String pendingRollerId = null;

    /** 同一时间只处理一次移动请求，避免双击等导致两子同时移动。 */
    private final Object moveRequestLock = new Object();

    private volatile boolean running = false;

    public HostServer(int port) {
        this.port = port;
        this.roomId = UUID.randomUUID().toString().substring(0, 8);
    }

    public String getRoomId() {
        return roomId;
    }

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    /**
     * 以房主本人的昵称初始化房间与 GameState。
     */
    public void initRoom(String hostPlayerId, String hostNickname) {
        roomInfo = new RoomInfo(roomId);
        // 先只创建房主玩家，其余玩家在加入时补充。
        gameState = new GameState(Collections.singletonList(hostNickname));
        Player hostPlayer = gameState.getPlayer(PlayerColor.GREEN);
        roomInfo.getPlayers().add(new PlayerInfo(hostPlayerId, hostNickname, hostPlayer.getColor(),
                PlayerState.ALIVE, false));
        playerColors.put(hostPlayerId, PlayerColor.GREEN);
        usedColors.add(PlayerColor.GREEN);
    }

    /**
     * 启动监听线程。
     */
    public void startAsync() {
        if (running) {
            return;
        }
        running = true;
        executor.execute(this::acceptLoop);
    }

    private void acceptLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("HostServer listening on port " + port + ", roomId=" + roomId);
            while (running) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                executor.execute(handler);
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        } finally {
            running = false;
        }
    }

    /**
     * 广播消息给所有客户端（含房主自己若需要）。
     */
    public void broadcast(Message message) {
        synchronized (clients) {
            for (ClientHandler handler : clients.values()) {
                handler.send(message);
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String playerId;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while (running) {
                    Object obj = in.readObject();
                    if (!(obj instanceof Message)) {
                        continue;
                    }
                    handleMessage((Message) obj);
                }
            } catch (EOFException eof) {
                // 客户端正常断开。
            } catch (IOException | ClassNotFoundException e) {
                if (running) {
                    e.printStackTrace();
                }
            } finally {
                if (playerId != null) {
                    clients.remove(playerId);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handleMessage(Message msg) {
            MessageType type = msg.getType();
            switch (type) {
                case JOIN_ROOM_REQ:
                    handleJoin(msg);
                    break;
                case RECONNECT_REQ:
                    handleReconnect(msg);
                    break;
                case PLAYER_READY:
                    handleReady(msg);
                    break;
                case START_GAME:
                    handleStartGame(msg);
                    break;
                case DICE_ROLL_REQUEST:
                    handleDiceRoll(msg);
                    break;
                case MOVE_REQUEST:
                    handleMoveRequest(msg);
                    break;
                case PING:
                    break;
                case COLOR_SELECT:
                    handleColorSelect(msg);
                    break;
                case DEBUG_DICE:
                    handleDebugDice(msg);
                    break;
                case DEBUG_PHASE_TOGGLE:
                    handleDebugPhase(msg);
                    break;
                default:
                    break;
            }
        }

        private void handleJoin(Message msg) {
            this.playerId = msg.getPlayerId();
            clients.put(playerId, this);
            String nickname = (msg.getPayload() instanceof String) ? (String) msg.getPayload() : ("Player");
            System.out.println("Player joined: " + playerId + " (" + nickname + ")");

            // 为新玩家分配颜色（房主已在 initRoom 中分配）
            if (!playerColors.containsKey(playerId)) {
                PlayerColor assigned = null;
                for (PlayerColor c : PlayerColor.ordered()) {
                    if (!usedColors.contains(c)) {
                        assigned = c;
                        break;
                    }
                }
                if (assigned == null) {
                    // 房间已满，简单忽略多余连接
                    return;
                }
                playerColors.put(playerId, assigned);
                usedColors.add(assigned);

                Player corePlayer = gameState.getPlayer(assigned);
                if (corePlayer != null) {
                    corePlayer.setNickname(nickname);
                }

                roomInfo.getPlayers().add(new PlayerInfo(playerId, nickname, assigned, PlayerState.ALIVE, false));
            }

            // 回应加入成功
            Message resp = new Message(MessageType.JOIN_ROOM_RESP, roomId, playerId, 0L, roomInfo);
            send(resp);

            // 向所有人推送房间最新状态
            broadcast(new Message(MessageType.ROOM_STATE_PUSH, roomId, playerId, 0L, roomInfo));
        }

        /**
         * 断线重连：复用原有 playerId 与颜色映射，并下发当前房间与对局快照。
         */
        private void handleReconnect(Message msg) {
            this.playerId = msg.getPlayerId();
            clients.put(playerId, this);

            // 简化：假设 playerId 已经存在并且房间仍然有效。
            Message roomSnapshot = new Message(MessageType.ROOM_STATE_PUSH, roomId, playerId, 0L, roomInfo);
            send(roomSnapshot);
            if (gameState != null) {
                Message gameSnapshot = new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, playerId, 0L, gameState);
                send(gameSnapshot);
            }

            Message resp = new Message(MessageType.RECONNECT_RESP, roomId, playerId, 0L, null);
            send(resp);
        }

        private void handleReady(Message msg) {
            if (roomInfo == null) {
                return;
            }
            for (PlayerInfo p : roomInfo.getPlayers()) {
                if (p.getPlayerId().equals(msg.getPlayerId())) {
                    p.setReady(!p.isReady());
                    System.out.println("Player ready toggled: " + p.getNickname() + " -> " + (p.isReady() ? "READY" : "NOT READY"));
                    break;
                }
            }
            broadcast(new Message(MessageType.ROOM_STATE_PUSH, roomId, msg.getPlayerId(), 0L, roomInfo));
        }

        private void handleColorSelect(Message msg) {
            if (roomInfo == null) {
                return;
            }
            Object payload = msg.getPayload();
            if (!(payload instanceof PlayerColor)) {
                return;
            }
            PlayerColor newColor = (PlayerColor) payload;
            PlayerColor oldColor = playerColors.get(msg.getPlayerId());
            if (oldColor == null || oldColor == newColor) {
                return;
            }
            // 目标颜色已被其他人占用则忽略
            if (usedColors.contains(newColor)) {
                return;
            }

            // 切换颜色
            usedColors.remove(oldColor);
            usedColors.add(newColor);
            playerColors.put(msg.getPlayerId(), newColor);

            // 更新 PlayerInfo
            for (PlayerInfo p : roomInfo.getPlayers()) {
                if (p.getPlayerId().equals(msg.getPlayerId())) {
                    p.setColor(newColor);
                    break;
                }
            }

            broadcast(new Message(MessageType.ROOM_STATE_PUSH, roomId, msg.getPlayerId(), 0L, roomInfo));
        }

        private void handleStartGame(Message msg) {
            if (roomInfo == null) {
                return;
            }
            // 简化：认为列表第一个玩家就是房主
            if (!roomInfo.getPlayers().isEmpty()
                    && !roomInfo.getPlayers().get(0).getPlayerId().equals(msg.getPlayerId())) {
                return;
            }
            roomInfo.setStarted(true);

            java.util.List<String> nicknames = new java.util.ArrayList<>();
            for (PlayerColor c : PlayerColor.ordered()) {
                String name = null;
                for (PlayerInfo p : roomInfo.getPlayers()) {
                    if (p.getColor() == c) {
                        name = p.getNickname();
                        break;
                    }
                }
                if (name == null) {
                    name = "AI-" + c.name();
                }
                nicknames.add(name);
            }
            gameState = new GameState(nicknames);

            broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
            synchronized (moveRequestLock) {
                runAITurnIfNeeded();
            }
        }

        private void handleDiceRoll(Message msg) {
            if (gameState == null) {
                return;
            }
            PlayerColor actorColor = playerColors.get(msg.getPlayerId());
            if (actorColor == null) {
                return;
            }
            if (gameState.getCurrentTurn() != actorColor) {
                return;
            }

            int dice = ruleEngine.rollDice();

            int count = gameState.getConsecutiveSixCount(actorColor);
            if (dice == 6) {
                count++;
            } else {
                count = 0;
            }
            gameState.setConsecutiveSixCount(actorColor, count);

            // 连续两次 6 复活被吃棋子
            if (count >= 2) {
                boolean revived = ruleEngine.reviveDeadPieces(gameState, actorColor);
                gameState.setConsecutiveSixCount(actorColor, 0);
                if (revived) {
                    // 复活消耗本次骰子，不可继续移动棋子
                    broadcast(new Message(MessageType.DICE_ROLL_RESULT, roomId, msg.getPlayerId(), 0L, dice));
                    synchronized (moveRequestLock) {
                        if (dice != 6) {
                            rotateTurn();
                        }
                        broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
                        runAITurnIfNeeded();
                    }
                    return;
                }
            }

            pendingDiceResult = dice;
            pendingRollerId = msg.getPlayerId();
            broadcast(new Message(MessageType.DICE_ROLL_RESULT, roomId, msg.getPlayerId(), 0L, dice));

            // 若无任何可走棋子（如未出飞机且未掷出 6），直接跳过本回合，轮到下一位
            List<Integer> movable = ruleEngine.listMovablePieces(gameState, actorColor, dice);
            if (movable.isEmpty()) {
                synchronized (moveRequestLock) {
                    pendingDiceResult = null;
                    pendingRollerId = null;
                    rotateTurn();
                    broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
                    runAITurnIfNeeded();
                }
            }
        }

        private void handleDebugDice(Message msg) {
            if (gameState == null) {
                return;
            }
            PlayerColor actorColor = playerColors.get(msg.getPlayerId());
            if (actorColor == null) {
                return;
            }
            if (gameState.getCurrentTurn() != actorColor) {
                return;
            }
            Object payload = msg.getPayload();
            if (!(payload instanceof Integer)) {
                return;
            }
            int dice = (Integer) payload;
            if (dice < 1 || dice > 6) {
                return;
            }

            int count = gameState.getConsecutiveSixCount(actorColor);
            if (dice == 6) {
                count++;
            } else {
                count = 0;
            }
            gameState.setConsecutiveSixCount(actorColor, count);

            // 连续两次 6 复活被吃棋子
            if (count >= 2) {
                boolean revived = ruleEngine.reviveDeadPieces(gameState, actorColor);
                gameState.setConsecutiveSixCount(actorColor, 0);
                if (revived) {
                    // 复活消耗本次骰子，不可继续移动棋子
                    broadcast(new Message(MessageType.DICE_ROLL_RESULT, roomId, msg.getPlayerId(), 0L, dice));
                    synchronized (moveRequestLock) {
                        if (dice != 6) {
                            rotateTurn();
                        }
                        broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
                        runAITurnIfNeeded();
                    }
                    return;
                }
            }

            pendingDiceResult = dice;
            pendingRollerId = msg.getPlayerId();
            broadcast(new Message(MessageType.DICE_ROLL_RESULT, roomId, msg.getPlayerId(), 0L, dice));

            List<Integer> movable = ruleEngine.listMovablePieces(gameState, actorColor, dice);
            if (movable.isEmpty()) {
                synchronized (moveRequestLock) {
                    pendingDiceResult = null;
                    pendingRollerId = null;
                    rotateTurn();
                    broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
                    runAITurnIfNeeded();
                }
            }
        }

        private void handleMoveRequest(Message msg) {
            synchronized (moveRequestLock) {
                if (gameState == null || pendingDiceResult == null || pendingRollerId == null) {
                    return;
                }
                if (!msg.getPlayerId().equals(pendingRollerId)) {
                    return;
                }
                Object payload = msg.getPayload();
                if (!(payload instanceof Integer)) {
                    return;
                }
                int pieceIndex = (Integer) payload;
                PlayerColor actorColor = playerColors.get(pendingRollerId);
                if (actorColor == null) {
                    return;
                }
                List<Integer> movable = ruleEngine.listMovablePieces(gameState, actorColor, pendingDiceResult);
                if (movable == null || !movable.contains(pieceIndex)) {
                    return;
                }
                MoveResult result = ruleEngine.movePiece(gameState, actorColor, pieceIndex, pendingDiceResult);
                pendingDiceResult = null;
                pendingRollerId = null;
                checkSuddenDeathTrigger();
                if (!result.isExtraTurn()) {
                    rotateTurn();
                }
                broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
                runAITurnIfNeeded();
            }
        }

        void send(Message msg) {
            try {
                // 确保发送同一对象的最新状态，而不是使用 Java 序列化缓存的旧快照。
                out.reset();
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** 当前回合颜色是否由已连接的真人玩家操作（否则由 AI 接管）。 */
    private boolean isColorHandledByHuman(PlayerColor color) {
        if (color == null) return false;
        for (Map.Entry<String, PlayerColor> e : playerColors.entrySet()) {
            if (e.getValue() == color && clients.containsKey(e.getKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 若当前回合无人操作（断线或空位），则由 AI 掷骰并选子移动；可连续多轮直到轮到真人。
     * 必须在已持有 moveRequestLock 时调用。
     */
    private void runAITurnIfNeeded() {
        if (gameState == null || roomInfo == null) return;
        while (!isColorHandledByHuman(gameState.getCurrentTurn())) {
            runOneAITurn();
            if (gameState == null) break;
        }
    }

    /** 执行一次 AI 回合：掷骰、选子、移动并广播。调用方需已持有 moveRequestLock。 */
    private void runOneAITurn() {
        if (gameState == null) return;
        PlayerColor color = gameState.getCurrentTurn();
        String aiPlayerId = "AI-" + color.name();

        int dice = ruleEngine.rollDice();
        int count = gameState.getConsecutiveSixCount(color);
        if (dice == 6) {
            count++;
        } else {
            count = 0;
        }
        gameState.setConsecutiveSixCount(color, count);

        // 连续两次 6 复活被吃棋子
        if (count >= 2) {
            boolean revived = ruleEngine.reviveDeadPieces(gameState, color);
            gameState.setConsecutiveSixCount(color, 0);
            if (revived) {
                // 复活消耗本次骰子，不可继续移动棋子
                broadcast(new Message(MessageType.DICE_ROLL_RESULT, roomId, aiPlayerId, 0L, dice));
                if (dice != 6) {
                    rotateTurn();
                }
                broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, aiPlayerId, 0L, gameState));
                return;
            }
        }

        broadcast(new Message(MessageType.DICE_ROLL_RESULT, roomId, aiPlayerId, 0L, dice));

        int pieceIndex = aiController.choosePiece(gameState, color, dice);
        if (pieceIndex < 0) {
            pendingDiceResult = null;
            pendingRollerId = null;
            rotateTurn();
            broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, aiPlayerId, 0L, gameState));
            return;
        }
        MoveResult result = ruleEngine.movePiece(gameState, color, pieceIndex, dice);
        pendingDiceResult = null;
        pendingRollerId = null;
        checkSuddenDeathTrigger();
        if (!result.isExtraTurn()) {
            rotateTurn();
        }
        broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, aiPlayerId, 0L, gameState));
    }

    /** 检测所有棋子归巢的玩家并标记为死亡。
     *  只有所有 4 架飞机都曾起飞过且当前全部归巢时，才判定死亡。 */
    private void checkAndMarkDeadPlayers() {
        if (gameState == null) return;
        for (PlayerColor color : PlayerColor.ordered()) {
            if (gameState.isPlayerDead(color)) continue;
            Player player = gameState.getPlayer(color);
            if (player == null) continue;
            int launchedCount = 0;
            int waitingCount = 0;
            for (Piece piece : player.getPieces()) {
                if (piece.hasEverLeftWaitingArea()) launchedCount++;
                if (piece.isInWaitingArea()) waitingCount++;
            }
            if (launchedCount >= 4 && waitingCount >= 4) {
                gameState.setPlayerDead(color, true);
                System.out.println("玩家 " + color + " 已死亡");
            }
        }
    }

    /**
     * 检查触发突然死亡模式的条件：
     * - 场上存活玩家 ≤ 2
     * - 或场上剩余非等待区棋子 ≤ 4
     */
    private void checkSuddenDeathTrigger() {
        if (gameState == null) return;
        checkAndMarkDeadPlayers();

        if (gameState.getPhase() == GamePhase.SUDDEN_DEATH) return;

        if (gameState.getAlivePlayerCount() <= 2 || gameState.getActivePieceCount() <= 4) {
            gameState.setPhase(GamePhase.SUDDEN_DEATH);
            System.out.println("=== 突然死亡模式开启 ===");
        }
    }

    /** 调试：强制切换到突然死亡模式。 */
    private void handleDebugPhase(Message msg) {
        if (gameState == null) return;
        gameState.setPhase(GamePhase.SUDDEN_DEATH);
        System.out.println("=== 突然死亡模式开启 (调试) ===");
        broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
    }

    private void rotateTurn() {
        if (gameState == null || roomInfo == null) {
            return;
        }
        PlayerColor current = gameState.getCurrentTurn();
        PlayerColor[] order = PlayerColor.ordered();
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                idx = i;
                break;
            }
        }
        for (int offset = 1; offset <= order.length; offset++) {
            PlayerColor next = order[(idx + offset) % order.length];
            boolean inRoom = false;
            for (PlayerInfo p : roomInfo.getPlayers()) {
                if (p.getColor() == next) {
                    inRoom = true;
                    break;
                }
            }
            if (inRoom) {
                gameState.setCurrentTurn(next);
                break;
            }
        }
    }
}

