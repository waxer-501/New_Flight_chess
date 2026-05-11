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
    COLOR_SELECT,
    CHAT_MESSAGE,

    // 对局
    START_GAME,
    DICE_ROLL_REQUEST,
    DICE_ROLL_RESULT,
    MOVE_REQUEST,
    MOVE_RESULT,
    /** 突然死亡模式：客户端发送单步移动方向，payload = int[]{ cellTypeOrdinal, positionIndex }。 */
    STEP_MOVE,
    /** 突然死亡模式双骰：payload = null 发起掷骰 / Integer 选择使用方式(1=dice1×2, 2=dice2×2, 3=相加)。 */
    DUAL_DICE_ROLL,
    /** 突然死亡模式双骰结果：payload = int[]{ dice1, dice2 }。 */
    DUAL_DICE_RESULT,
    GAME_STATE_SNAPSHOT,
    GAME_OVER,

    // 连接维护
    PING,
    PONG,
    RECONNECT_REQ,
    RECONNECT_RESP,
    HOST_MIGRATION_NOTICE,

    // 调试
    DEBUG_DICE,
    DEBUG_PHASE_TOGGLE
}

