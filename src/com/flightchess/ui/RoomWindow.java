package com.flightchess.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.flightchess.net.PlayerInfo;
import com.flightchess.net.RoomInfo;

/**
 * 房间界面：显示玩家列表与准备/开始按钮。
 */
public class RoomWindow extends JFrame {

    private final UiController controller;

    private final JTextArea playersArea = new JTextArea(10, 30);
    private final JButton readyBtn = new JButton("准备");
    private final JButton startBtn = new JButton("开始游戏（房主）");

    public RoomWindow(UiController controller, String title) {
        super(title);
        this.controller = controller;
        initUi();
    }

    private void initUi() {
        setLayout(new BorderLayout());
        playersArea.setEditable(false);
        add(new JScrollPane(playersArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(readyBtn);
        bottom.add(startBtn);
        add(bottom, BorderLayout.SOUTH);

        readyBtn.addActionListener((ActionEvent e) -> controller.toggleReady());
        startBtn.addActionListener((ActionEvent e) -> controller.startGame());

        pack();
        setLocationRelativeTo(null);
    }

    public void updateRoomInfo(RoomInfo roomInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("房间号: ").append(roomInfo.getRoomId()).append("\n");
        List<PlayerInfo> players = roomInfo.getPlayers();
        for (PlayerInfo p : players) {
            sb.append(p.getNickname())
              .append(" [").append(p.getColor()).append("] ")
              .append(p.getState())
              .append(p.isReady() ? " (已准备)" : " (未准备)")
              .append("\n");
        }
        playersArea.setText(sb.toString());
    }

    public void setHost(boolean isHost) {
        startBtn.setEnabled(isHost);
    }
}

