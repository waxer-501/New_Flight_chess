package com.flightchess.net;

import java.io.Serializable;

/**
 * 通用消息封装。
 *
 * 为保持简单，payload 使用 Object，发送与接收两端使用相同版本的类。
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String roomId;
    private String playerId;
    private long seq;
    private long timestamp;
    private Object payload;

    public Message() {
    }

    public Message(MessageType type, String roomId, String playerId, long seq, Object payload) {
        this.type = type;
        this.roomId = roomId;
        this.playerId = playerId;
        this.seq = seq;
        this.timestamp = System.currentTimeMillis();
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}

