package com.flightchess.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.flightchess.core.PlayerColor;
import com.flightchess.net.PlayerInfo;
import com.flightchess.net.RoomInfo;

/**
 * 房间界面：显示玩家列表与准备/开始按钮。
 */
public class RoomWindow extends JFrame {

    private final UiController controller;

    private final JTextArea playersArea = new JTextArea(10, 30);
    private final JComboBox<PlayerColor> colorCombo = new JComboBox<>(PlayerColor.values());
    private final JButton readyBtn = new JButton("准备");
    private final JButton startBtn = new JButton("开始游戏（房主）");
    private boolean updatingCombo = false;
    private Set<PlayerColor> takenByOthers = new HashSet<>();

    public RoomWindow(UiController controller, String title) {
        super(title);
        this.controller = controller;
        initUi();
    }

    private void initUi() {
        setLayout(new BorderLayout());
        playersArea.setEditable(false);
        add(new JScrollPane(playersArea), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout());
        top.add(new JLabel("选择颜色:"));
        top.add(colorCombo);
        add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(readyBtn);
        bottom.add(startBtn);
        add(bottom, BorderLayout.SOUTH);

        readyBtn.addActionListener((ActionEvent e) -> controller.toggleReady());
        startBtn.addActionListener((ActionEvent e) -> controller.startGame());

        // 用自定义渲染器将已被他人占用的颜色显示为灰色
        colorCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PlayerColor && takenByOthers.contains((PlayerColor) value)) {
                    c.setEnabled(false);
                }
                return c;
            }
        });

        colorCombo.addActionListener((ActionEvent e) -> {
            if (updatingCombo) return;
            PlayerColor selected = (PlayerColor) colorCombo.getSelectedItem();
            if (selected != null) {
                if (takenByOthers.contains(selected)) {
                    // 不能选择已被占用的颜色，通过服务器下发更新回退
                    return;
                }
                controller.selectColor(selected);
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    public void updateRoomInfo(RoomInfo roomInfo, String myPlayerId) {
        // 收集已被其他玩家占用的颜色
        Set<PlayerColor> takenByOthers = new HashSet<>();
        PlayerColor myColor = null;
        for (PlayerInfo p : roomInfo.getPlayers()) {
            if (myPlayerId != null && myPlayerId.equals(p.getPlayerId())) {
                myColor = p.getColor();
            } else {
                takenByOthers.add(p.getColor());
            }
        }

        // 更新颜色下拉框状态
        updatingCombo = true;
        this.takenByOthers = takenByOthers;
        if (myColor != null) {
            colorCombo.setSelectedItem(myColor);
        }
        colorCombo.repaint();
        updatingCombo = false;

        // 更新玩家列表
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

    public void setColorEnabled(boolean enabled) {
        colorCombo.setEnabled(enabled);
    }
}

