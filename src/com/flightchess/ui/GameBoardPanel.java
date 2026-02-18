package com.flightchess.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

import com.flightchess.core.GameState;
import com.flightchess.core.PlayerColor;

/**
 * 棋盘面板：当前仅绘制外圈格子（按用户要求逐步排版）。
 */
public class GameBoardPanel extends JPanel {

    private static final int BOARD_SIZE = 720;
    /** 外圈每边 13 格（与 BoardConfig 52 格一致） */
    private static final int OUTER_SIDE = 13;
    /** 长方形格子长边（2:1 中的长） */
    private static final int CELL_LONG = 48;
    /** 长方形格子短边（2:1 中的短） */
    private static final int CELL_SHORT = CELL_LONG / 2;

    private GameState gameState;

    public GameBoardPanel() {
        setPreferredSize(new Dimension(BOARD_SIZE, BOARD_SIZE));
        setBackground(new Color(0xF5, 0xF5, 0xF5));
    }

    public void setGameState(GameState state) {
        this.gameState = state;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        int halfOuter = OUTER_SIDE * CELL_LONG / 2;
        int left = cx - halfOuter;
        int top = cy - halfOuter;
        int right = cx + halfOuter;
        int bottom = cy + halfOuter;

        drawOuterTrack(g2, left, top, right, bottom);
    }

    private void drawOuterTrack(Graphics2D g2, int left, int top, int right, int bottom) {
        int cx = getWidth() / 2;
        int h = getHeight();
        int L = CELL_LONG;
        int S = CELL_SHORT;
        PlayerColor[] order = PlayerColor.ordered();
        // 索引 0：棋盘最下方中间，竖放
        int x0 = cx - S / 2;
        int y0 = h - L;
        drawTrackRect(g2, x0, y0, S, L, order[0]);
        // 索引 1：在 0 的左边，竖放，长边相邻
        int x1 = cx - 3 * S / 2;
        drawTrackRect(g2, x1, y0, S, L, order[1]);
        // 索引 2：在 1 的左边，三角形，两直角边朝上、朝右，右侧直角边与 1 左边缘贴齐
        int x2Left = cx - 5 * S / 2 - S;
        drawRightTriangleCell(g2, x2Left, y0, L, TriangleCorner.BOTTOM_RIGHT, order[2]);
        // 索引 3：在 2 的三角形上方，横放的长方形，下长边与三角形上边缘贴齐
        drawTrackRect(g2, x2Left, y0 - S, L, S, order[3]);
    }

    /**
     * 长方形格子模板，长宽比 2:1。竖放时 w=S,h=L；横放时 w=L,h=S。
     */
    private void drawTrackRect(Graphics2D g2, int x, int y, int w, int h, PlayerColor zoneColor) {
        g2.setColor(colorFor(zoneColor));
        g2.fillRect(x, y, w, h);
        g2.setColor(Color.WHITE);
        g2.drawRect(x, y, w, h);
        int r = Math.min(w, h) / 3;
        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillOval(x + (w - r * 2) / 2, y + (h - r * 2) / 2, r * 2, r * 2);
    }

    /** 直角三角形格子模板，直角边长 leg（与长方形长边相同）。(left,top) 为包围盒左上角。 */
    private enum TriangleCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }
    private void drawRightTriangleCell(Graphics2D g2, int left, int top, int leg, TriangleCorner corner, PlayerColor zoneColor) {
        int[] xs, ys;
        switch (corner) {
            case TOP_LEFT:
                xs = new int[] { left + leg, left + leg, left };
                ys = new int[] { top, top + leg, top + leg };
                break;
            case TOP_RIGHT:
                xs = new int[] { left, left, left + leg };
                ys = new int[] { top, top + leg, top + leg };
                break;
            case BOTTOM_RIGHT:
                xs = new int[] { left + leg, left, left + leg };
                ys = new int[] { top, top, top + leg };
                break;
            case BOTTOM_LEFT:
                xs = new int[] { left, left + leg, left };
                ys = new int[] { top, top, top + leg };
                break;
            default:
                return;
        }
        g2.setColor(colorFor(zoneColor));
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(Color.WHITE);
        g2.drawPolygon(xs, ys, 3);
        int cx = (xs[0] + xs[1] + xs[2]) / 3;
        int cy = (ys[0] + ys[1] + ys[2]) / 3;
        int r = leg / 4;
        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    private int[] getCellBounds(int index, int left, int top, int right, int bottom) {
        int cx = getWidth() / 2;
        int h = getHeight();
        int S = CELL_SHORT;
        int L = CELL_LONG;
        int y0 = h - L;
        if (index == 0) {
            return new int[] { cx - S / 2, y0, S, L };
        }
        if (index == 1) {
            return new int[] { cx - 3 * S / 2, y0, S, L };
        }
        if (index == 2) {
            return new int[] { cx - 5 * S / 2 - S, y0, L, L };
        }
        if (index == 3) {
            int x2Left = cx - 5 * S / 2 - S;
            return new int[] { x2Left, y0 - S, L, S };
        }
        return new int[] { 0, 0, 0, 0 };
    }

    /** 外圈索引转格子中心像素（画棋子用）。 */
    private int[] outerIndexToPixel(int index, int left, int top, int right, int bottom) {
        int cx = getWidth() / 2;
        int h = getHeight();
        int S = CELL_SHORT;
        int L = CELL_LONG;
        int yCenter = h - L / 2;
        if (index == 0) {
            return new int[] { cx, yCenter };
        }
        if (index == 1) {
            return new int[] { cx - S, yCenter };
        }
        if (index == 2) {
            int x2Left = cx - 5 * S / 2 - S;
            int y0 = h - L;
            return new int[] { x2Left + 2 * L / 3, y0 + L / 3 };
        }
        if (index == 3) {
            int x2Left = cx - 5 * S / 2 - S;
            int y0 = h - L;
            return new int[] { x2Left + L / 2, y0 - S / 2 };
        }
        return new int[] { cx, h / 2 };
    }

    private Color colorFor(PlayerColor c) {
        switch (c) {
            case GREEN:
                return new Color(0x2E, 0x7D, 0x32);
            case RED:
                return new Color(0xC6, 0x28, 0x28);
            case BLUE:
                return new Color(0x15, 0x65, 0xC0);
            case YELLOW:
                return new Color(0xF9, 0xA8, 0x25);
            default:
                return Color.BLACK;
        }
    }
}
