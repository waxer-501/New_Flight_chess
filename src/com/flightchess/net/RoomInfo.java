package com.flightchess.net;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 房间信息。
 */
public class RoomInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String roomId;
    private boolean started;
    private List<PlayerInfo> players = new ArrayList<>();

    public RoomInfo() {
    }

    public RoomInfo(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public List<PlayerInfo> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerInfo> players) {
        this.players = players;
    }
}

