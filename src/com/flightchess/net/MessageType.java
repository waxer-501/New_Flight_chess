package com.flightchess.net;

/**
 * 消息类型定义。
 *
 * 所有消息都通过 Java 对象序列化在 TCP 连接上传输。
 */
public enum MessageType {
    // 房间与大厅
    CREATE_ROOM_REQ,
    CREATE_ROOM_RESP,
    JOIN_ROOM_REQ,
    JOIN_ROOM_RESP,
    ROOM_STATE_PUSH,
    PLAYER_READY,
    CHAT_MESSAGE,

    // 对局
    START_GAME,
    DICE_ROLL_REQUEST,
    DICE_ROLL_RESULT,
    MOVE_REQUEST,
    MOVE_RESULT,
    GAME_STATE_SNAPSHOT,
    GAME_OVER,

    // 连接维护
    PING,
    PONG,
    RECONNECT_REQ,
    RECONNECT_RESP,
    HOST_MIGRATION_NOTICE
}

