package com.flightchess.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import com.flightchess.core.GamePhase;
import com.flightchess.core.GameState;
import com.flightchess.core.PlayerColor;

/**
 * 对局窗口：包含棋盘与掷骰/日志区域。
 */
public class GameWindow extends JFrame {

    /** 调试模式：改为 true 后可在对局中按 G 键直接指定骰子值。发布时置为 false。 */
    private static final boolean DEBUG_MODE = true;

    private final UiController controller;
    private final GameBoardPanel boardPanel = new GameBoardPanel();

    private final JLabel infoLabel = new JLabel("等待开始...");
    private final JButton rollDiceBtn = new JButton("掷骰");
    /** 突然死亡模式双骰按钮。 */
    private final JButton singleDoubleBtn = new JButton("单骰×2");
    private final JButton doubleSumBtn = new JButton("双骰相加");
    private final JLabel suddenDeathBanner = new JLabel("突然死亡模式开启", SwingConstants.CENTER);

    /** 是否处于步控模式（←→键控制棋子逐格移动）。 */
    private boolean stepControlActive = false;

    public GameWindow(UiController controller) {
        super("Flight Chess - Game");
        this.controller = controller;
        initUi();
    }

    private void initUi() {
        setLayout(new BorderLayout());
        add(boardPanel, BorderLayout.CENTER);

        suddenDeathBanner.setFont(new Font("微软雅黑", Font.BOLD, 26));
        suddenDeathBanner.setForeground(new Color(0xC6, 0x28, 0x28));
        suddenDeathBanner.setOpaque(true);
        suddenDeathBanner.setBackground(new Color(0xFF, 0xEB, 0x3B));
        suddenDeathBanner.setVisible(false);
        add(suddenDeathBanner, BorderLayout.NORTH);

        // 从项目 docs 目录加载棋子图（透明背景 PNG）
        boardPanel.setPieceImage(PlayerColor.RED, "docs/chess_red.png");
        boardPanel.setPieceImage(PlayerColor.YELLOW, "docs/chess_yellow.png");
        boardPanel.setPieceImage(PlayerColor.BLUE, "docs/chess_blue.png");
        boardPanel.setPieceImage(PlayerColor.GREEN, "docs/chess_green.png");
        boardPanel.setOnMovablePieceClick(pieceIndex -> controller.requestMove(pieceIndex));

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(infoLabel);
        bottom.add(rollDiceBtn);
        bottom.add(singleDoubleBtn);
        bottom.add(doubleSumBtn);
        add(bottom, BorderLayout.SOUTH);

        rollDiceBtn.addActionListener((ActionEvent e) -> controller.requestDiceRoll());
        singleDoubleBtn.addActionListener((ActionEvent e) -> controller.requestDualDiceRoll(1));
        doubleSumBtn.addActionListener((ActionEvent e) -> controller.requestDualDiceRoll(2));

        // 调试功能：按 G 键弹出骰子输入框（仅 DEBUG_MODE=true 时启用）
        if (DEBUG_MODE) {
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false), "debugDice");
            getRootPane().getActionMap().put("debugDice", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String input = JOptionPane.showInputDialog(
                            GameWindow.this, "输入骰子值 (1-6):", "调试骰子", JOptionPane.PLAIN_MESSAGE);
                    if (input != null && !input.trim().isEmpty()) {
                        try {
                            int dice = Integer.parseInt(input.trim());
                            if (dice >= 1 && dice <= 6) {
                                controller.debugDice(dice);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            });
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0, false), "debugPhase");
            getRootPane().getActionMap().put("debugPhase", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    controller.debugTogglePhase();
                }
            });
        }

        setupStepControlKeys();

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    public void setGameState(GameState state) {
        boardPanel.setGameState(state);
        if (state != null && state.getPhase() == GamePhase.SUDDEN_DEATH) {
            suddenDeathBanner.setVisible(true);
            // 进入突然死亡模式后切换至双骰按钮
            showDualDiceButtons(true);
        } else {
            showDualDiceButtons(false);
        }
    }

    /** 切换正常掷骰与双骰按钮显隐。 */
    private void showDualDiceButtons(boolean dual) {
        rollDiceBtn.setVisible(!dual);
        singleDoubleBtn.setVisible(dual);
        doubleSumBtn.setVisible(dual);
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
        singleDoubleBtn.setEnabled(enabled);
        doubleSumBtn.setEnabled(enabled);
    }

    // ===== 突然死亡模式步控 =====

    /** 进入步控模式：禁骰子按钮，绑定方向键，显示可走格子。 */
    public void enterStepControlMode() {
        stepControlActive = true;
        rollDiceBtn.setEnabled(false);
        singleDoubleBtn.setEnabled(false);
        doubleSumBtn.setEnabled(false);
        boardPanel.setStepControlActive(true);
    }

    /** 退出步控模式。 */
    public void exitStepControlMode() {
        stepControlActive = false;
        boardPanel.setStepControlActive(false);
    }

    /** 设置步控方向键绑定（在 initUi 中调用一次）。 */
    private void setupStepControlKeys() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "stepLeft");
        getRootPane().getActionMap().put("stepLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!stepControlActive) return;
                int[] t = boardPanel.getAdjacentForKey(KeyEvent.VK_LEFT);
                if (t != null) controller.requestStepMove(t[0], t[1]);
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "stepRight");
        getRootPane().getActionMap().put("stepRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!stepControlActive) return;
                int[] t = boardPanel.getAdjacentForKey(KeyEvent.VK_RIGHT);
                if (t != null) controller.requestStepMove(t[0], t[1]);
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "stepUp");
        getRootPane().getActionMap().put("stepUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!stepControlActive) return;
                int[] t = boardPanel.getAdjacentForKey(KeyEvent.VK_UP);
                if (t != null) controller.requestStepMove(t[0], t[1]);
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "stepDown");
        getRootPane().getActionMap().put("stepDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!stepControlActive) return;
                int[] t = boardPanel.getAdjacentForKey(KeyEvent.VK_DOWN);
                if (t != null) controller.requestStepMove(t[0], t[1]);
            }
        });
    }
}