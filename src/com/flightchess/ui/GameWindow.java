package com.flightchess.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.flightchess.core.GameState;
import com.flightchess.core.PlayerColor;

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

        // 从项目 docs 目录加载棋子图（透明背景 PNG）
        boardPanel.setPieceImage(PlayerColor.RED, "docs/chess_red.png");
        boardPanel.setPieceImage(PlayerColor.YELLOW, "docs/chess_yellow.png");
        boardPanel.setPieceImage(PlayerColor.BLUE, "docs/chess_blue.png");
        boardPanel.setPieceImage(PlayerColor.GREEN, "docs/chess_green.png");
        boardPanel.setOnMovablePieceClick(pieceIndex -> controller.requestMove(pieceIndex));

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(infoLabel);
        bottom.add(rollDiceBtn);
        add(bottom, BorderLayout.SOUTH);

        rollDiceBtn.addActionListener((ActionEvent e) -> controller.requestDiceRoll());

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    public void setGameState(GameState state) {
        boardPanel.setGameState(state);
    }

    /** 设置最近一次掷骰结果及掷骰者，用于在可移动棋子上显示悬停黑圈。 */
    public void setLastDiceResult(int dice, com.flightchess.core.PlayerColor rollerColor) {
        boardPanel.setLastDiceResult(dice, rollerColor);
    }

    /** 设置当前掷骰者是否为本地玩家（仅此时可点击选子）。 */
    public void setRollerIsLocal(boolean local) {
        boardPanel.setRollerIsLocal(local);
    }

    public void setInfoText(String text) {
        infoLabel.setText(text);
    }

    public void setDiceEnabled(boolean enabled) {
        rollDiceBtn.setEnabled(enabled);
    }
}

