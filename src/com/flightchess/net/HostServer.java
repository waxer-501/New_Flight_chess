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
import com.flightchess.core.GameState;
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

            // 连续两次 6 复活逻辑
            int count = gameState.getConsecutiveSixCount(actorColor);
            if (dice == 6) {
                count++;
                if (count >= 2) {
                    reviveOnePiece(actorColor);
                    count = 0;
                }
            } else {
                count = 0;
            }
            gameState.setConsecutiveSixCount(actorColor, count);

            pendingDiceResult = dice;
            pendingRollerId = msg.getPlayerId();
            broadcast(new Message(MessageType.DICE_ROLL_RESULT, roomId, msg.getPlayerId(), 0L, dice));
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
                if (!result.isExtraTurn()) {
                    rotateTurn();
                }
                broadcast(new Message(MessageType.GAME_STATE_SNAPSHOT, roomId, msg.getPlayerId(), 0L, gameState));
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

    /**
     * 在等待复活区为指定玩家复活一架棋子，放回其起飞格。
     */
    private void reviveOnePiece(PlayerColor color) {
        if (gameState == null) {
            return;
        }
        Player player = gameState.getPlayer(color);
        if (player == null) {
            return;
        }
        for (com.flightchess.core.Piece piece : player.getPieces()) {
            if (piece.isInWaitingArea()) {
                int startIndex = com.flightchess.core.BoardConfig.getStartIndex(color);
                piece.setCellType(com.flightchess.core.CellType.OUTER);
                piece.setPositionIndex(startIndex);
                break;
            }
        }
    }
}

