package com.flightchess.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩家信息与棋子集合。
 */
public class Player implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id; // 网络中的 playerId，可用 UUID 或昵称+随机串
    private String nickname;
    private final PlayerColor color;
    private PlayerState state;
    private final List<Piece> pieces = new ArrayList<>();

    public Player(String id, String nickname, PlayerColor color, int pieceCount) {
        this.id = id;
        this.nickname = nickname;
        this.color = color;
        this.state = PlayerState.ALIVE;
        for (int i = 0; i < pieceCount; i++) {
            pieces.add(new Piece(color));
        }
    }

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public PlayerColor getColor() {
        return color;
    }

    public PlayerState getState() {
        return state;
    }

    public void setState(PlayerState state) {
        this.state = state;
    }

    public List<Piece> getPieces() {
        return pieces;
    }
}

