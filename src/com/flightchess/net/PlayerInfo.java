package com.flightchess.net;

import java.io.Serializable;

import com.flightchess.core.PlayerColor;
import com.flightchess.core.PlayerState;

/**
 * 面向网络传输的玩家信息摘要。
 */
public class PlayerInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String playerId;
    private String nickname;
    private PlayerColor color;
    private PlayerState state;
    private boolean ready;

    public PlayerInfo() {
    }

    public PlayerInfo(String playerId, String nickname, PlayerColor color, PlayerState state, boolean ready) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.color = color;
        this.state = state;
        this.ready = ready;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getNickname() {
        return nickname;
    }

    public PlayerColor getColor() {
        return color;
    }

    public PlayerState getState() {
        return state;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}

