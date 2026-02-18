package com.flightchess.net;

/**
 * 断线重连辅助类。
 *
 * 当前版本实现一个非常简单的方案：
 * - 记录最近一次成功连接的 host/port/roomId/playerId；
 * - 在需要时重新创建 ClientConnection 并发送 RECONNECT_REQ；
 * - 由 HostServer 返回 ROOM_STATE_PUSH 与 GAME_STATE_SNAPSHOT。
 */
public class ReconnectManager {

    private String lastHost;
    private int lastPort;
    private String lastRoomId;
    private String lastPlayerId;

    public void recordSession(String host, int port, String roomId, String playerId) {
        this.lastHost = host;
        this.lastPort = port;
        this.lastRoomId = roomId;
        this.lastPlayerId = playerId;
    }

    /**
     * 使用之前记录的会话信息构造新的连接，并发送 RECONNECT_REQ。
     */
    public ClientConnection attemptReconnect(MessageHandler handler) {
        if (lastHost == null || lastRoomId == null || lastPlayerId == null) {
            return null;
        }
        ClientConnection conn = new ClientConnection();
        conn.setMessageHandler(handler::onMessage);
        try {
            conn.connect(lastHost, lastPort);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            return null;
        }
        // 连接建立后，应用层应在合适的时机调用 sendReconnect。
        return conn;
    }

    public void sendReconnect(ClientConnection conn) {
        if (conn == null || lastRoomId == null || lastPlayerId == null) {
            return;
        }
        Message msg = new Message(MessageType.RECONNECT_REQ, lastRoomId, lastPlayerId, 0L, null);
        conn.send(msg);
    }

    /**
     * 供 UI 或控制器实现的简易回调接口。
     */
    public interface MessageHandler {
        void onMessage(Message message);
    }
}


