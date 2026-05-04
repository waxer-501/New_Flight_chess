package com.flightchess.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import com.flightchess.core.CellType;
import com.flightchess.core.GameState;
import com.flightchess.core.Piece;
import com.flightchess.core.Player;
import com.flightchess.core.PlayerColor;
import com.flightchess.core.RuleEngine;

/**
 * Board panel: draws birth zones, takeoff zones, outer track and pieces.
 */
public class GameBoardPanel extends JPanel {

    private static final double BOARD_SIZE = 1080.0;


    private static final double BOARD_SCALE = 0.80;

/** 13 cells per side, matching 52 outer cells. */
    private static final int OUTER_SIDE = 13;

/** Long edge of a 2:1 rectangular cell. */
    private static final double CELL_LONG = BOARD_SIZE / 8.5 * BOARD_SCALE;

/** Short edge of a 2:1 rectangular cell. */
    private static final double CELL_SHORT = CELL_LONG / 2;

    private GameState gameState;

    /** Piece images by color; if absent, that color is not drawn. */
    private final Map<PlayerColor, Image> pieceImages = new EnumMap<>(PlayerColor.class);

    /** Rule engine used by UI for movable hints. */
    private final RuleEngine ruleEngine = new RuleEngine();

    /** Last dice result and roller color, used for hover highlight. */
    private int lastDiceResult = -1;
    private PlayerColor lastDiceRollerColor;

    /** Mouse coordinates inside panel for hover highlight. */
    private int hoverX = -1, hoverY = -1;

    /** Whether current roller is local player (click-to-select enabled). */
    private boolean rollerIsLocal = false;

    /** Callback when clicking a movable piece (piece index 0~3). */
    private IntConsumer onMovablePieceClick;

    /** 已发送一次移动请求，在收到新状态前忽略再次点击，避免双击导致两子同时移动。 */
    private volatile boolean moveRequestPending = false;

    /** Rotation center for each quarter: 0=bottom,1=left,2=top,3=right. */
    private int[] quarterCenterX = { -1, (int)BOARD_SIZE / 2 , (int)BOARD_SIZE / 2, (int)BOARD_SIZE / 2 };
    private int[] quarterCenterY = { -1, (int)BOARD_SIZE / 2 , (int)BOARD_SIZE / 2, (int)BOARD_SIZE / 2 };

    /** Set rotation center for quarter (0~3). */
    public void setQuarterCenter(int quarter, int centerX, int centerY) {
        if (quarter >= 0 && quarter <= 3) {
            quarterCenterX[quarter] = centerX;
            quarterCenterY[quarter] = centerY;
        }
    }

    public GameBoardPanel() {
        setPreferredSize(new Dimension((int) Math.round(BOARD_SIZE), (int) Math.round(BOARD_SIZE)));
        setBackground(new Color(0xF5, 0xF5, 0xF5));
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = e.getX();
                hoverY = e.getY();
                repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                hoverY = -1;
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onMovablePieceClick == null || !rollerIsLocal || gameState == null) return;
                if (moveRequestPending) return; // 已发过移动请求，等状态更新后再响应，避免双击触发两次
                if (lastDiceResult <= 0 || lastDiceRollerColor == null || gameState.getCurrentTurn() != lastDiceRollerColor) return;
                List<Integer> movable = ruleEngine.listMovablePieces(gameState, lastDiceRollerColor, lastDiceResult);
                if (movable == null || movable.isEmpty()) return;
                int size = (int) Math.max(16, CELL_LONG * 0.45);
                int mx = e.getX(), my = e.getY();
                for (PlayerColor color : PlayerColor.ordered()) {
                    if (color != lastDiceRollerColor) continue;
                    Player player = gameState.getPlayer(color);
                    if (player == null) continue;
                    int slot = 0;
                    for (Piece piece : player.getPieces()) {
                        int[] xy;
                        if (piece.getCellType() == CellType.WAITING_AREA) {
                            xy = birthPointSlotToPixel(colorToBirthIndex(color), slot);
                        } else if (piece.getCellType() == CellType.TAKEOFF) {
                            xy = takeoffIndexToPixel(piece.getPositionIndex());
                        } else if (piece.getCellType() == CellType.OUTER) {
                            xy = outerIndexToPixel(piece.getPositionIndex(), 0, 0, 0, 0);
                        } else {
                            slot++;
                            continue;
                        }
                        int x = xy[0] - size / 2, y = xy[1] - size / 2;
                        if (mx >= x && mx < x + size && my >= y && my < y + size && movable.contains(slot)) {
                            moveRequestPending = true;
                            onMovablePieceClick.accept(slot);
                            return;
                        }
                        slot++;
                    }
                }
            }
        });
    }

    public void setGameState(GameState state) {
        this.gameState = state;
        moveRequestPending = false; // 收到新状态，允许下一次选子
        if (lastDiceRollerColor != null && state != null && state.getCurrentTurn() != lastDiceRollerColor) {
            lastDiceResult = -1;
            lastDiceRollerColor = null;
        }
        repaint();
    }

    /** Set latest dice + roller color for movable hover highlight. */
    public void setLastDiceResult(int dice, PlayerColor rollerColor) {
        this.lastDiceResult = dice;
        this.lastDiceRollerColor = rollerColor;
        repaint();
    }

    /** Enable/disable local click-to-select mode. */
    public void setRollerIsLocal(boolean local) {
        this.rollerIsLocal = local;
    }

    /** Set callback when clicking a movable piece (index 0~3). */
    public void setOnMovablePieceClick(IntConsumer listener) {
        this.onMovablePieceClick = listener;
    }

    /** Set piece image for one color. */
    public void setPieceImage(PlayerColor color, Image image) {
        if (color != null) {
            pieceImages.put(color, image);
            repaint();
        }
    }

    /** Load piece image from file path (PNG/JPG supported). */
    public void setPieceImage(PlayerColor color, File file) {
        if (color == null || file == null || !file.isFile()) return;
        try {
            pieceImages.put(color, ImageIO.read(file));
            repaint();
        } catch (IOException ignored) { }
    }

    /** Load piece image from string path. */
    public void setPieceImage(PlayerColor color, String path) {
        if (path != null) setPieceImage(color, new File(path));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double halfOuter = OUTER_SIDE * CELL_LONG / 2.0;
        double left = cx - halfOuter;
        double top = cy - halfOuter;
        double right = cx + halfOuter;
        double bottom = cy + halfOuter;

        drawBirthPoints(g2, w, h);
        drawOuterTrack(g2, left, top, right, bottom);
        drawPieces(g2);
    }

    /** Draw four birth zones and matching takeoff markers. */
    private void drawBirthPoints(Graphics2D g2, int w, int h) {
        int side = (int) Math.round(CELL_LONG * 2.0);
        int[] xs = { 0, w - side, w - side, 0 };
        int[] ys = { 0, 0, h - side, h - side };
        int[] takeoffXs = { 0, (int) Math.round(w - side / 3.5 * 4.5), (int) Math.round(w - side / 3.5), side }; // Keep consistent with drawTakeoffArea radius math.
        int[] takeoffYs = { 0, 0 - side, h - 2 * side - (int) Math.round(side / 3.5), h - (int) Math.round(side / 3.5 * 4.5) };
        PlayerColor[] colors = { PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN };
        for (int i = 0; i < 4; i++) {
            drawBirthPointCell(g2, xs[i], ys[i], colors[i]);
            drawBirthPointIndex(g2, xs[i], ys[i], side, i);
            drawTakeoffArea(g2, takeoffXs[i], takeoffYs[i], side, colors[i]);
        }
    }

    /** Draw index label at the center of a birth zone cell. */
    private void drawBirthPointIndex(Graphics2D g2, int cellX, int cellY, int side, int index) {
        g2.setColor(Color.BLACK);
        String str = String.valueOf(index);
        FontMetrics fm = g2.getFontMetrics();
        int tx = cellX + (side - fm.stringWidth(str)) / 2;
        int ty = cellY + (side + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(str, tx, ty);
    }

    /**
     * Convert birth-zone index to pixel center.
     * @param index 0=top-left, 1=top-right, 2=bottom-right, 3=bottom-left
     * @return [x, y] center pixel; invalid index returns [0, 0]
     */
    public int[] birthPointIndexToPixel(int index) {
        if (index < 0 || index > 3) {
            return new int[] { 0, 0 };
        }
        int w = getWidth() > 0 ? getWidth() : (int) Math.round(BOARD_SIZE);
        int h = getHeight() > 0 ? getHeight() : (int) Math.round(BOARD_SIZE);
        int side = (int) Math.round(CELL_LONG * 2.0);
        int half = side / 2;
        int[] cx = { half, w - half, w - half, half };
        int[] cy = { half, half, h - half, h - half };
        return new int[] { cx[index], cy[index] };
    }

    /** Pixel center of one slot in a birth zone (slot 0~3). */
    private int[] birthPointSlotToPixel(int birthIndex, int slot) {
        if (birthIndex < 0 || birthIndex > 3 || slot < 0 || slot > 3) return new int[] { 0, 0 };
        int w = getWidth() > 0 ? getWidth() : (int) Math.round(BOARD_SIZE);
        int h = getHeight() > 0 ? getHeight() : (int) Math.round(BOARD_SIZE);
        int side = (int) Math.round(CELL_LONG * 2.0);
        double r = side / 7.0;
        int[] cellX = { 0, w - side, w - side, 0 };
        int[] cellY = { 0, 0, h - side, h - side };
        int x = cellX[birthIndex], y = cellY[birthIndex];
        double cx = x + (slot % 2 == 0 ? 2 * r : side - 2 * r);
        double cy = y + (slot < 2 ? 2 * r : side - 2 * r);
        return new int[] { (int) Math.round(cx), (int) Math.round(cy) };
    }

    private static int colorToBirthIndex(PlayerColor c) {
        switch (c) {
            case YELLOW: return 0;
            case BLUE:   return 1;
            case RED:    return 2;
            case GREEN:  return 3;
            default:     return 0;
        }
    }

    /** Map logical takeoff index (-1..-4) to screen pixel center. */
    private int[] takeoffIndexToPixel(int takeoffIndex) {
        int w = getWidth() > 0 ? getWidth() : (int) Math.round(BOARD_SIZE);
        int h = getHeight() > 0 ? getHeight() : (int) Math.round(BOARD_SIZE);
        int side = (int) Math.round(CELL_LONG * 2.0);
        int[] takeoffXs = {
            0,
            (int) Math.round(w - side / 3.5 * 4.5),
            (int) Math.round(w - side / 3.5),
            side
        };
        int[] takeoffYs = {
            0,
            -side,
            h - 2 * side - (int) Math.round(side / 3.5),
            h - (int) Math.round(side / 3.5 * 4.5)
        };
        int r = (int) Math.round(side / 3.5);
        int idx;
        switch (takeoffIndex) {
            case -1: idx = 0; break; // Yellow
            case -2: idx = 1; break; // Blue
            case -3: idx = 2; break; // Red
            case -4: idx = 3; break; // Green
            default: return new int[] { w / 2, h / 2 };
        }
        int ix = takeoffXs[idx];
        int iy = (int) Math.round(takeoffYs[idx] + side);
        return new int[] { ix + r / 2, iy + r / 2 };
    }

    /** Draw pieces; hover movable piece to show black ring hint. */
    private void drawPieces(Graphics2D g2) {
        if (gameState == null) return;
        int size = (int) Math.max(16, CELL_LONG * 0.45);
        List<Integer> movable = null;
        if (lastDiceResult > 0 && lastDiceRollerColor != null && gameState.getCurrentTurn() == lastDiceRollerColor) {
            movable = ruleEngine.listMovablePieces(gameState, lastDiceRollerColor, lastDiceResult);
        }
        for (PlayerColor color : PlayerColor.ordered()) {
            Image img = pieceImages.get(color);
            if (img == null) continue;
            Player player = gameState.getPlayer(color);
            if (player == null) continue;
            int slot = 0;
            for (Piece piece : player.getPieces()) {
                int[] xy;
                if (piece.getCellType() == CellType.WAITING_AREA) {
                    xy = birthPointSlotToPixel(colorToBirthIndex(color), slot);
                } else if (piece.getCellType() == CellType.TAKEOFF) {
                    xy = takeoffIndexToPixel(piece.getPositionIndex());
                } else if (piece.getCellType() == CellType.OUTER) {
                    xy = outerIndexToPixel(piece.getPositionIndex(), 0, 0, 0, 0);
                } else {
                    slot++;
                    continue;
                }
                int x = xy[0] - size / 2, y = xy[1] - size / 2;
                Shape oldClip = g2.getClip();
                g2.setClip(new Ellipse2D.Double(x, y, size, size));
                g2.drawImage(img, x, y, size, size, null);
                g2.setClip(oldClip);
                boolean isMovable = movable != null && color == lastDiceRollerColor && movable.contains(slot);
                boolean isHovered = hoverX >= x && hoverX < x + size && hoverY >= y && hoverY < y + size;
                if (isMovable && isHovered) {
                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(2.5f));
                    int r = size / 2 + 3;
                    g2.drawOval(xy[0] - r, xy[1] - r, r * 2, r * 2);
                }
                slot++;
            }
        }
    }

    private void drawOuterTrack(Graphics2D g2, double left, double top, double right, double bottom) {
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double L = CELL_LONG;
        double S = CELL_SHORT;
        PlayerColor[] order = PlayerColor.ordered();
        // Index 0: center-bottom vertical rectangle.
        double x0 = cx - S / 2.0;
        double y0 = h - L;
        drawTrackRect(g2, x0, y0, S, L, order[0]);
        // Index 1: left of index 0, vertical rectangle.
        double x1 = cx - 3.0 * S / 2.0;
        drawTrackRect(g2, x1, y0, S, L, order[1]);
        double x2 = cx - 5.0 * S / 2.0;
        drawTrackRect(g2, x2, y0, S, L, order[2]);
        // Index 2: left of index 1, right-angle triangle.
        double x2Left = cx - 7.0 * S / 2.0 - S;
        drawRightTriangleCell(g2, x2Left, y0, L, TriangleCorner.BOTTOM_RIGHT, order[3]);
        drawTrackRect(g2, x2Left, y0 - S, L, S, order[4 % 4]);
        drawTrackRect(g2, x2Left, y0 - S * 2.0, L, S, order[5 % 4]);
        drawRightTriangleCell(g2, x2Left, y0 - S * 4.0, L, TriangleCorner.TOP_LEFT, order[6 % 4]);
        drawRightTriangleCell(g2, x2Left, y0 - S * 4.0, L, TriangleCorner.BOTTOM_LEFT, order[7 % 4]);
        drawTrackRect(g2, x2Left - S, y0 - 2.0 * L, S, L, order[8 % 4]);
        drawTrackRect(g2, x2Left - 2.0 * S, y0 - 2.0 * L, S, L, order[9 % 4]);
        double x10Left = x2Left - 2.0 * L;
        drawRightTriangleCell(g2, x10Left, y0 - 2.0 * L, L, TriangleCorner.BOTTOM_RIGHT, order[10 % 4]);
        drawTrackRect(g2, x10Left, y0 - 2.0 * L - S, L, S, order[11 % 4]);
        drawTrackRect(g2, x10Left, y0 - 2.0 * L - S * 2.0, L, S, order[12 % 4]);

        for (int q = 1; q <= 3; q++) {
            drawQuarterRotated(g2, cx, h, q, L, S, order);
        }
    }

    private void drawTakeoffArea(Graphics2D g2, double x, double y, double side, PlayerColor color) {
        g2.setColor(colorFor(color));
        int r = (int) Math.round(side / 3.5);
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y + side);
        g2.fillOval(ix, iy, r, r);
        g2.setColor(Color.WHITE);
        String str = "GO";
        FontMetrics fm = g2.getFontMetrics();
        int cx = ix + r / 2;
        int cy = iy + r / 2;
        int tx = cx - fm.stringWidth(str) / 2;
        int ty = cy + (fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(str, tx, ty);
    }

    /** Rotate point around (cx,cy) clockwise by steps*90 in screen coords. */
    private static double[] rotatePoint(double x, double y, double cx, double cy, int steps) {
        double dx = x - cx, dy = y - cy;
        for (int i = 0; i < steps; i++) {
            double t = dx;
            dx = -dy;
            dy = t;
        }
        return new double[] { cx + dx, cy + dy };
    }

    /** Quarter-to-rotation-step mapping: 0=bottom,1=left,2=top,3=right. */
    private static int quarterToRotationSteps(int quarter) {
        return quarter;
    }

    
    /** Draw quarter 13 cells by rotating the base segment. */
    private void drawQuarterRotated(Graphics2D g2, double cx, double h, int quarter, double L, double S, PlayerColor[] order) {
        double y0 = h - L;
        double x2Left = cx - 7.0 * S / 2.0 - S;
        double x10Left = x2Left - 2.0 * L;
        
        int baseIdx = quarter * 13 + 2;
        if(quarter == 1) {
            baseIdx = 1;
        }
        else if(quarter == 2) {
            baseIdx = 2;
        }
        else if(quarter == 3) {
            baseIdx = 3;
        }
        double rotCx = quarterCenterX[quarter] >= 0 ? quarterCenterX[quarter] : cx;
        double rotCy = quarterCenterY[quarter] >= 0 ? quarterCenterY[quarter] : h / 2.0;
        drawRotatedRect(g2, cx - S / 2.0, y0, S, L, rotCx, rotCy, quarter, order[(baseIdx + 0) % 4]);
        drawRotatedRect(g2, cx - 3.0 * S / 2.0, y0, S, L, rotCx, rotCy, quarter, order[(baseIdx + 1) % 4]);
        drawRotatedRect(g2, cx - 5.0 * S / 2.0, y0, S, L, rotCx, rotCy, quarter, order[(baseIdx + 2) % 4]);
        drawRotatedTriangle(g2, x2Left, y0, L, TriangleCorner.BOTTOM_RIGHT, rotCx, rotCy, quarter, order[(baseIdx + 3) % 4]);
        drawRotatedRect(g2, x2Left, y0 - S, L, S, rotCx, rotCy, quarter, order[(baseIdx + 4) % 4]);
        if(quarter == 4) {
            x10Left = x2Left - 2.0 * L;
        }
        drawRotatedRect(g2, x2Left, y0 - S * 2.0, L, S, rotCx, rotCy, quarter, order[(baseIdx + 5) % 4]);
        drawRotatedTriangle(g2, x2Left, y0 - S * 4.0, L, TriangleCorner.TOP_LEFT, rotCx, rotCy, quarter, order[(baseIdx + 6) % 4]);
        drawRotatedTriangle(g2, x2Left, y0 - S * 4.0, L, TriangleCorner.BOTTOM_LEFT, rotCx, rotCy, quarter, order[(baseIdx + 7) % 4]);
        drawRotatedRect(g2, x2Left - S, y0 - 2.0 * L, S, L, rotCx, rotCy, quarter, order[(baseIdx + 8) % 4]);
        drawRotatedRect(g2, x2Left - 2.0 * S, y0 - 2.0 * L, S, L, rotCx, rotCy, quarter, order[(baseIdx + 9) % 4]);
        drawRotatedTriangle(g2, x10Left, y0 - 2.0 * L, L, TriangleCorner.BOTTOM_RIGHT, rotCx, rotCy, quarter, order[(baseIdx + 10) % 4]);
        drawRotatedRect(g2, x10Left, y0 - 2.0 * L - S, L, S, rotCx, rotCy, quarter, order[(baseIdx + 11) % 4]);
        drawRotatedRect(g2, x10Left, y0 - 2.0 * L - S * 2.0, L, S, rotCx, rotCy, quarter, order[(baseIdx + 12) % 4]);
    }

    private void drawRotatedRect(Graphics2D g2, double x, double y, double w, double h, double cx, double cy, int quarter, PlayerColor color) {
        int steps = quarterToRotationSteps(quarter);
        double centerX = x + w / 2.0, centerY = y + h / 2.0;
        double[] c = rotatePoint(centerX, centerY, cx, cy, steps);
        double nw = (steps % 2 == 0) ? w : h;
        double nh = (steps % 2 == 0) ? h : w;
        drawTrackRect(g2, c[0] - nw / 2.0, c[1] - nh / 2.0, nw, nh, color);
    }

    private void drawRotatedTriangle(Graphics2D g2, double left, double top, double leg, TriangleCorner corner, double cx, double cy, int quarter, PlayerColor color) {
        double[] xs, ys;
        switch (corner) {
            case TOP_LEFT:
                xs = new double[] { left + leg, left + leg, left };
                ys = new double[] { top, top + leg, top + leg };
                break;
            case TOP_RIGHT:
                xs = new double[] { left, left, left + leg };
                ys = new double[] { top, top + leg, top + leg };
                break;
            case BOTTOM_RIGHT:
                xs = new double[] { left + leg, left, left + leg };
                ys = new double[] { top, top, top + leg };
                break;
            case BOTTOM_LEFT:
                xs = new double[] { left, left + leg, left };
                ys = new double[] { top, top, top + leg };
                break;
            default:
                return;
        }
        int[] xr = new int[3], yr = new int[3];
        int steps = quarterToRotationSteps(quarter);
        for (int i = 0; i < 3; i++) {
            double[] p = rotatePoint(xs[i], ys[i], cx, cy, steps);
            xr[i] = (int) Math.round(p[0]);
            yr[i] = (int) Math.round(p[1]);
        }
        g2.setColor(colorFor(color));
        g2.fillPolygon(xr, yr, 3);
        g2.setColor(Color.WHITE);
        g2.drawPolygon(xr, yr, 3);
        int cx0 = (xr[0] + xr[1] + xr[2]) / 3;
        int cy0 = (yr[0] + yr[1] + yr[2]) / 3;
        int r = (int) Math.round(leg / 4.0);
        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillOval(cx0 - r, cy0 - r, r * 2, r * 2);
    }

    /**
     * Rectangle cell template (2:1). Vertical: w=S,h=L; Horizontal: w=L,h=S.
     */
    private void drawTrackRect(Graphics2D g2, double x, double y, double w, double h, PlayerColor zoneColor) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y), iw = (int) Math.round(w), ih = (int) Math.round(h);
        g2.setColor(colorFor(zoneColor));
        g2.fillRect(ix, iy, iw, ih);
        g2.setColor(Color.WHITE);
        g2.drawRect(ix, iy, iw, ih);
        int r = Math.min(iw, ih) / 3;
        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillOval(ix + (iw - r * 2) / 2, iy + (ih - r * 2) / 2, r * 2, r * 2);
    }

    /** Birth-zone template: square, (x,y) is top-left. */
    private void drawBirthPointCell(Graphics2D g2, int x, int y, PlayerColor zoneColor) {
        int side = (int) Math.round(CELL_LONG * 2.0);
        g2.setColor(colorFor(zoneColor));
        g2.fillRect(x, y, side, side);
        g2.setColor(Color.WHITE);
        g2.drawRect(x, y, side, side);
        double radius = side / 7.0;
        int cx = (int) (x + radius );
        int cy = (int) (y + radius );
        int d = (int) (radius * 2);
        g2.fillOval(cx, cy, d, d);
        g2.setColor(Color.BLACK);
        g2.drawOval(cx, cy, d, d);
        cy = (int) (y + side - radius * 3);
        g2.setColor(Color.WHITE);
        g2.fillOval(cx, cy, d, d);
        g2.setColor(Color.BLACK);
        g2.drawOval(cx, cy, d, d);
        cx = (int) (x + side - radius * 3);
        cy = (int) (y + radius);
        g2.setColor(Color.WHITE);
        g2.fillOval(cx, cy, d, d);
        g2.setColor(Color.BLACK);
        g2.drawOval(cx, cy, d, d);
        cy = (int) (y + side - radius * 3);
        g2.setColor(Color.WHITE);
        g2.fillOval(cx, cy, d, d);
        g2.setColor(Color.BLACK);
        g2.drawOval(cx, cy, d, d);
    }

    /** Right-triangle cell template, leg is side length, (left,top) is bbox top-left. */
    private enum TriangleCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }
    private void drawRightTriangleCell(Graphics2D g2, double left, double top, double leg, TriangleCorner corner, PlayerColor zoneColor) {
        double[] xs, ys;
        switch (corner) {
            case TOP_LEFT:
                xs = new double[] { left + leg, left + leg, left };
                ys = new double[] { top, top + leg, top + leg };
                break;
            case TOP_RIGHT:
                xs = new double[] { left, left, left + leg };
                ys = new double[] { top, top + leg, top + leg };
                break;
            case BOTTOM_RIGHT:
                xs = new double[] { left + leg, left, left + leg };
                ys = new double[] { top, top, top + leg };
                break;
            case BOTTOM_LEFT:
                xs = new double[] { left, left + leg, left };
                ys = new double[] { top, top, top + leg };
                break;
            default:
                return;
        }
        int[] ixs = new int[] { (int) Math.round(xs[0]), (int) Math.round(xs[1]), (int) Math.round(xs[2]) };
        int[] iys = new int[] { (int) Math.round(ys[0]), (int) Math.round(ys[1]), (int) Math.round(ys[2]) };
        g2.setColor(colorFor(zoneColor));
        g2.fillPolygon(ixs, iys, 3);
        g2.setColor(Color.WHITE);
        g2.drawPolygon(ixs, iys, 3);
        int cx = (ixs[0] + ixs[1] + ixs[2]) / 3;
        int cy = (iys[0] + iys[1] + iys[2]) / 3;
        int r = (int) Math.round(leg / 4.0);
        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    /**
     * Get bounding box [x, y, width, height] for an outer-track index.
     * index: 0~51, where 0~12 bottom, 13~25 left, 26~38 top, 39~51 right.
     * left/top/right/bottom params are kept for compatibility.
     */
    public int[] getCellBounds(int index, int left, int top, int right, int bottom) {
        if (index < 0 || index >= 52) {
            return new int[] { 0, 0, 0, 0 };
        }
        int quarter = index / 13;
        int localIdx = index % 13;
        double[] bounds = getSegment0CellBounds(localIdx);
        if (quarter == 0) {
            return new int[] {
                (int) Math.round(bounds[0]), (int) Math.round(bounds[1]),
                (int) Math.round(bounds[2]), (int) Math.round(bounds[3])
            };
        }
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double rotCx = quarterCenterX[quarter] >= 0 ? quarterCenterX[quarter] : cx;
        double rotCy = quarterCenterY[quarter] >= 0 ? quarterCenterY[quarter] : h / 2.0;
        double[] rotated = rotateRect(bounds[0], bounds[1], bounds[2], bounds[3], rotCx, rotCy, quarter);
        return new int[] {
            (int) Math.round(rotated[0]), (int) Math.round(rotated[1]),
            (int) Math.round(rotated[2]), (int) Math.round(rotated[3])
        };
    }

    /** Bounds for segment-0 cells (index 0~12), consistent with drawOuterTrack. */
    private double[] getSegment0CellBounds(int localIdx) {
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double L = CELL_LONG;
        double S = CELL_SHORT;
        double y0 = h - L;
        double x2Left = cx - 7.0 * S / 2.0 - S;
        double x10Left = x2Left - 2.0 * L;
        switch (localIdx) {
            case 0:  return new double[] { cx - S / 2.0, y0, S, L };
            case 1:  return new double[] { cx - 3.0 * S / 2.0, y0, S, L };
            case 2:  return new double[] { cx - 5.0 * S / 2.0, y0, S, L };
            case 3:  return new double[] { x2Left, y0, L, L };           // triangle
            case 4:  return new double[] { x2Left, y0 - S, L, S };
            case 5:  return new double[] { x2Left, y0 - S * 2.0, L, S };
            case 6:  return new double[] { x2Left, y0 - S * 4.0, L, L }; // triangle
            case 7:  return new double[] { x2Left, y0 - S * 4.0, L, L }; // triangle
            case 8:  return new double[] { x2Left - S, y0 - 2.0 * L, S, L };
            case 9:  return new double[] { x2Left - 2.0 * S, y0 - 2.0 * L, S, L };
            case 10: return new double[] { x10Left, y0 - 2.0 * L, L, L };  // triangle
            case 11: return new double[] { x10Left, y0 - 2.0 * L - S, L, S };
            case 12: return new double[] { x10Left, y0 - 2.0 * L - S * 2.0, L, S };
            default: return new double[] { 0, 0, 0, 0 };
        }
    }

    /** Rotate a rectangle around (rotCx,rotCy) by quarter steps; return [x',y',w',h']. */
    private static double[] rotateRect(double x, double y, double w, double h, double rotCx, double rotCy, int quarter) {
        int steps = quarterToRotationSteps(quarter);
        double centerX = x + w / 2.0, centerY = y + h / 2.0;
        double[] c = rotatePoint(centerX, centerY, rotCx, rotCy, steps);
        double nw = (steps % 2 == 0) ? w : h;
        double nh = (steps % 2 == 0) ? h : w;
        return new double[] { c[0] - nw / 2.0, c[1] - nh / 2.0, nw, nh };
    }

    /** Convert outer-track index to cell center pixel, matching drawOuterTrack layout. */
    private int[] outerIndexToPixel(int index, int left, int top, int right, int bottom) {
        if (index < 0 || index >= 52) {
            return new int[] { (int) Math.round(getWidth() / 2.0), (int) Math.round(getHeight() / 2.0) };
        }
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double L = CELL_LONG;
        double S = CELL_SHORT;
        int quarter = index / 13;
        int localIdx = index % 13;
        double[] base = outerQuarter0Center(cx, h, L, S, localIdx);
        if (quarter == 0) {
            return new int[] { (int) Math.round(base[0]), (int) Math.round(base[1]) };
        }
        double rotCx = quarterCenterX[quarter] >= 0 ? quarterCenterX[quarter] : cx;
        double rotCy = quarterCenterY[quarter] >= 0 ? quarterCenterY[quarter] : h / 2.0;
        int steps = quarterToRotationSteps(quarter);
        double[] r = rotatePoint(base[0], base[1], rotCx, rotCy, steps);
        return new int[] { (int) Math.round(r[0]), (int) Math.round(r[1]) };
    }

    /** Unrotated centers for segment-0 (13 cells), aligned with drawOuterTrack. */
    private static double[] outerQuarter0Center(double cx, double h, double L, double S, int localIdx) {
        double y0 = h - L;
        double x2Left = cx - 7.0 * S / 2.0 - S;
        double x10Left = x2Left - 2.0 * L;
        switch (localIdx) {
            case 0:
                return new double[] { cx, y0 + L / 2.0 };
            case 1:
                return new double[] { cx - S, y0 + L / 2.0 };
            case 2:
                return new double[] { cx - 2.0 * S, y0 + L / 2.0 };
            case 3:
                return new double[] { x2Left + 2.0 * L / 3.0, y0 + L / 3.0 };
            case 4:
                return new double[] { x2Left + L / 2.0, y0 - S / 2.0 };
            case 5:
                return new double[] { x2Left + L / 2.0, y0 - S * 2.0 + S / 2.0 };
            case 6:
                return new double[] { x2Left + 2.0 * L / 3.0, y0 - S * 4.0 + 2.0 * L / 3.0 };
            case 7:
                return new double[] { x2Left + L / 3.0, y0 - S * 4.0 + L / 3.0 };
            case 8:
                return new double[] { x2Left - S / 2.0, y0 - 2.0 * L + L / 2.0 };
            case 9:
                return new double[] { x2Left - 2.0 * S + S / 2.0, y0 - 2.0 * L + L / 2.0 };
            case 10:
                return new double[] { x10Left + 2.0 * L / 3.0, y0 - 2.0 * L + L / 3.0 };
            case 11:
                return new double[] { x10Left + L / 2.0, y0 - 2.0 * L - S / 2.0 };
            case 12:
                return new double[] { x10Left + L / 2.0, y0 - 2.0 * L - S * 2.0 + S / 2.0 };
            default:
                return new double[] { cx, h / 2.0 };
        }
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
