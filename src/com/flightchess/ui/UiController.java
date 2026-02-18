package com.flightchess.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.SwingUtilities;

import com.flightchess.core.GameState;
import com.flightchess.net.ClientConnection;
import com.flightchess.net.HostServer;
import com.flightchess.net.Message;
import com.flightchess.net.MessageType;
import com.flightchess.net.NetConfig;
import com.flightchess.net.RoomInfo;

/**
 * 负责 UI 与网络层之间的协调。
 */
public class UiController {

    private final MainWindow mainWindow;

    private HostServer hostServer;
    private ClientConnection client;

    private RoomWindow roomWindow;
    private GameWindow gameWindow;

    private String playerId;
    private String roomId;
    private boolean isHost;

    private RoomInfo currentRoomInfo;
    private GameState currentGameState;

    public UiController(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
    }

    public void createAndHostRoom(String nickname) {
        isHost = true;
        playerId = UUID.randomUUID().toString();

        hostServer = new HostServer(NetConfig.DEFAULT_PORT);
        hostServer.initRoom(playerId, nickname);
        hostServer.startAsync();
        roomId = hostServer.getRoomId();

        connectAsClient("127.0.0.1", NetConfig.DEFAULT_PORT);

        send(new Message(MessageType.JOIN_ROOM_REQ, roomId, playerId, 0L, nickname));
        openRoomWindow();
    }

    public void joinRoom(String host, int port, String nickname) {
        isHost = false;
        playerId = UUID.randomUUID().toString();
        connectAsClient(host, port);
        // 房间号由主机在响应里告知，先发送空 roomId。
        send(new Message(MessageType.JOIN_ROOM_REQ, null, playerId, 0L, nickname));
        openRoomWindow();
    }

    private void connectAsClient(String host, int port) {
        client = new ClientConnection();
        client.setMessageHandler(this::onMessageReceived);
        try {
            client.connect(host, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void send(Message msg) {
        if (client != null) {
            client.send(msg);
        }
    }

    private void openRoomWindow() {
        SwingUtilities.invokeLater(() -> {
            if (roomWindow == null) {
                roomWindow = new RoomWindow(this, "房间 - " + (isHost ? "房主" : "玩家"));
                roomWindow.setHost(isHost);
                // 如果在打开窗口之前已经收到了房间信息，立刻刷新一次界面
                if (currentRoomInfo != null) {
                    roomWindow.updateRoomInfo(currentRoomInfo);
                }
            }
            roomWindow.setVisible(true);
        });
    }

    private void openGameWindow() {
        SwingUtilities.invokeLater(() -> {
            if (gameWindow == null) {
                gameWindow = new GameWindow(this);
                if (currentGameState != null) {
                    gameWindow.setGameState(currentGameState);
                }
            }
            gameWindow.setVisible(true);
        });
    }

    private void onMessageReceived(Message msg) {
        MessageType type = msg.getType();
        if (type == MessageType.JOIN_ROOM_RESP || type == MessageType.ROOM_STATE_PUSH) {
            RoomInfo info = (RoomInfo) msg.getPayload();
            this.roomId = info.getRoomId();
            this.currentRoomInfo = info;
            if (roomWindow != null) {
                SwingUtilities.invokeLater(() -> roomWindow.updateRoomInfo(info));
            }
        } else if (type == MessageType.GAME_STATE_SNAPSHOT) {
            GameState state = (GameState) msg.getPayload();
            this.currentGameState = state;
            // 收到第一帧状态时，如果还没有对局窗口，则先创建窗口。
            if (gameWindow == null) {
                openGameWindow();
            }
            if (gameWindow != null) {
                SwingUtilities.invokeLater(() -> gameWindow.setGameState(state));
            }
        } else if (type == MessageType.DICE_ROLL_RESULT) {
            Object payload = msg.getPayload();
            if (payload instanceof Integer && gameWindow != null) {
                int dice = (Integer) payload;
                SwingUtilities.invokeLater(() -> gameWindow.setInfoText("掷骰结果: " + dice));
            }
        }
    }

    // ===== 房间操作 =====

    public void toggleReady() {
        send(new Message(MessageType.PLAYER_READY, roomId, playerId, 0L, null));
    }

    public void startGame() {
        if (!isHost) {
            return;
        }
        send(new Message(MessageType.START_GAME, roomId, playerId, 0L, null));
        openGameWindow();
    }

    // ===== 对局操作 =====

    public void requestDiceRoll() {
        send(new Message(MessageType.DICE_ROLL_REQUEST, roomId, playerId, 0L, null));
    }
}

