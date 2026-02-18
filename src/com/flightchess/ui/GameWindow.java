package com.flightchess.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.flightchess.core.GameState;

/**
 * 对局窗口：包含棋盘与掷骰/日志区域。
 */
public class GameWindow extends JFrame {

    private final UiController controller;
    private final GameBoardPanel boardPanel = new GameBoardPanel();

    private final JLabel infoLabel = new JLabel("等待开始...");
    private final JButton rollDiceBtn = new JButton("掷骰");

    public GameWindow(UiController controller) {
        super("Flight Chess - Game");
        this.controller = controller;
        initUi();
    }

    private void initUi() {
        setLayout(new BorderLayout());
        add(boardPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(infoLabel);
        bottom.add(rollDiceBtn);
        add(bottom, BorderLayout.SOUTH);

        rollDiceBtn.addActionListener((ActionEvent e) -> controller.requestDiceRoll());

        pack();
        setLocationRelativeTo(null);
    }

    public void setGameState(GameState state) {
        boardPanel.setGameState(state);
    }

    public void setInfoText(String text) {
        infoLabel.setText(text);
    }

    public void setDiceEnabled(boolean enabled) {
        rollDiceBtn.setEnabled(enabled);
    }
}

