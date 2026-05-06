package com.flightchess.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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

    private static final double BOARD_SIZE = 940.0;

/** 13 cells per side, matching 52 outer cells. */
    private static final int OUTER_SIDE = 13;

/** Long edge of a 2:1 rectangular cell, recomputed in paintComponent. */
    private double cellLong = BOARD_SIZE / 8.5;

/** Short edge of a 2:1 rectangular cell, recomputed in paintComponent. */
    private double cellShort = cellLong / 2;

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

    /** Rotation center for each quarter: computed in paintComponent. */
    private int[] quarterCenterX = { -1, -1, -1, -1 };
    private int[] quarterCenterY = { -1, -1, -1, -1 };

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
                int size = (int) Math.max(16, cellLong * 0.45);
                int mx = e.getX(), my = e.getY();
                for (PlayerColor color : PlayerColor.ordered()) {
                    if (color != lastDiceRollerColor) continue;
                    Player player = gameState.getPlayer(color);
                    if (player == null) continue;
                    int slot = 0;
                    for (Piece piece : player.getPieces()) {
                        int[] xy = pieceToPixel(piece, slot);
                        if (xy == null) { slot++; continue; }
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

    public void setOnMovablePieceClick(IntConsumer listener) {
        this.onMovablePieceClick = listener;
    }

    // ===== 突然死亡模式步控 =====

    /** 是否处于步控模式（已选子，等待键盘方向）。 */
    private boolean stepControlActive = false;

    public void setStepControlActive(boolean active) {
        this.stepControlActive = active;
        repaint();
    }

    /** 获取当前选中棋子的相邻可达位置（用于键盘映射），空列表表示无选项。 */
    public List<int[]> getAdjacentForSelectedPiece() {
        if (gameState == null || gameState.getSelectedPieceIndex() < 0) return List.of();
        PlayerColor color = gameState.getCurrentTurn();
        Player player = gameState.getPlayer(color);
        if (player == null) return List.of();
        List<Piece> pieces = player.getPieces();
        int idx = gameState.getSelectedPieceIndex();
        if (idx < 0 || idx >= pieces.size()) return List.of();
        return ruleEngine.getAdjacentPositions(gameState, pieces.get(idx));
    }

    /** 根据按键返回对应的相邻目标。中心格时 ↑↓←→ 直连四条终点通道。 */
    public int[] getAdjacentForKey(int vkCode) {
        if (gameState == null || gameState.getSelectedPieceIndex() < 0) return null;
        PlayerColor color = gameState.getCurrentTurn();
        Player player = gameState.getPlayer(color);
        if (player == null) return null;
        List<Piece> pieces = player.getPieces();
        int idx = gameState.getSelectedPieceIndex();
        if (idx < 0 || idx >= pieces.size()) return null;
        Piece piece = pieces.get(idx);
        List<int[]> targets = ruleEngine.getAdjacentPositions(gameState, piece);

        // 中心格：↑↓←→ 直接映射到 BLUE/GREEN/RED/YELLOW 终点通道
        if (piece.getCellType() == CellType.CENTER && targets.size() >= 4) {
            // targets 顺序 = [GREEN, RED, BLUE, YELLOW]
            switch (vkCode) {
                case java.awt.event.KeyEvent.VK_UP:    return targets.get(2); // BLUE
                case java.awt.event.KeyEvent.VK_DOWN:  return targets.get(0); // GREEN
                case java.awt.event.KeyEvent.VK_LEFT:  return targets.get(1); // RED
                case java.awt.event.KeyEvent.VK_RIGHT: return targets.get(3); // YELLOW
            }
        }

        // 其他格子：普通索引映射
        if (targets.isEmpty()) return null;
        // 有 3+ 个方向时 ↑ 取第三个
        if (vkCode == java.awt.event.KeyEvent.VK_UP && targets.size() >= 3) {
            return targets.get(2);
        }
        // 有 2 个方向时 ← 取第一个(顺时针)，→ 取第二个(逆时针)
        if (vkCode == java.awt.event.KeyEvent.VK_LEFT && targets.size() >= 2) {
            return targets.get(1);  // ← = 顺时针
        }
        if (vkCode == java.awt.event.KeyEvent.VK_RIGHT) {
            return targets.get(0);  // → = 逆时针
        }
        if (vkCode == java.awt.event.KeyEvent.VK_LEFT) {
            return targets.get(0);  // 仅 1 个方向时 ← 也可用
        }
        return null;
    }

    /** Get pixel center of a piece, or null if invalid position. */
    private int[] pieceToPixel(Piece piece, int slot) {
        if (piece.getCellType() == CellType.WAITING_AREA) {
            return birthPointSlotToPixel(colorToBirthIndex(piece.getOwner()), slot);
        } else if (piece.getCellType() == CellType.TAKEOFF) {
            return takeoffIndexToPixel(piece.getPositionIndex());
        } else if (piece.getCellType() == CellType.OUTER) {
            return outerIndexToPixel(piece.getPositionIndex(), 0, 0, 0, 0);
        } else if (piece.getCellType() == CellType.CENTER_PATH) {
            return stepTargetPixel(CellType.CENTER_PATH.ordinal(), piece.getPositionIndex());
        } else if (piece.getCellType() == CellType.CENTER) {
            return stepTargetPixel(CellType.CENTER.ordinal(), piece.getPositionIndex());
        }
        return null;
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

        // 根据面板实际尺寸动态计算缩放，避免不同DPI下棋盘错位
        double scale = Math.min(w, h) / BOARD_SIZE;
        cellLong = BOARD_SIZE / 8.5 * scale;
        cellShort = cellLong / 2;

        double cx = w / 2.0;
        double cy = h / 2.0;

        // 同步所有象限的旋转中心到面板实际中心，避免偏移
        for (int i = 0; i < 4; i++) {
            quarterCenterX[i] = (int) Math.round(cx);
            quarterCenterY[i] = (int) Math.round(cy);
        }

        double halfOuter = OUTER_SIDE * cellLong / 2.0;
        double left = cx - halfOuter;
        double top = cy - halfOuter;
        double right = cx + halfOuter;
        double bottom = cy + halfOuter;

        drawBirthPoints(g2, w, h);
        drawOuterTrack(g2, left, top, right, bottom);
        drawPieces(g2);
        if (stepControlActive) {
            drawStepTargets(g2);
        }
    }

    /** Draw four birth zones and matching takeoff markers. */
    private void drawBirthPoints(Graphics2D g2, int w, int h) {
        int side = (int) Math.round(cellLong * 2.0);
        int[] xs = { 0, w - side, w - side, 0 };
        int[] ys = { 0, 0, h - side, h - side };
        int[] takeoffXs = { 0, (int) Math.round(w - side / 3.5 * 4.5), (int) Math.round(w - side / 3.5), side }; // Keep consistent with drawTakeoffArea radius math.
        int[] takeoffYs = { 0, 0 - side, h - 2 * side - (int) Math.round(side / 3.5), h - (int) Math.round(side / 3.5 * 4.5) };
        PlayerColor[] colors = { PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW, PlayerColor.GREEN };
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
        int side = (int) Math.round(cellLong * 2.0);
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
        int side = (int) Math.round(cellLong * 2.0);
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
            case RED: return 0;
            case BLUE:   return 1;
            case YELLOW:    return 2;
            case GREEN:  return 3;
            default:     return 0;
        }
    }

    /** Map logical takeoff index (-1..-4) to screen pixel center. */
    private int[] takeoffIndexToPixel(int takeoffIndex) {
        int w = getWidth() > 0 ? getWidth() : (int) Math.round(BOARD_SIZE);
        int h = getHeight() > 0 ? getHeight() : (int) Math.round(BOARD_SIZE);
        int side = (int) Math.round(cellLong * 2.0);
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
            case -1: idx = 0; break; // Red
            case -2: idx = 1; break; // Blue
            case -3: idx = 2; break; // Yellow
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
        int size = (int) Math.max(16, cellLong * 0.45);
        List<Integer> movable = null;
        if (lastDiceResult > 0 && lastDiceRollerColor != null
                && gameState.getCurrentTurn() == lastDiceRollerColor) {
            movable = ruleEngine.listMovablePieces(gameState, lastDiceRollerColor, lastDiceResult);
        }

        // Collect positions of OUTER/TAKEOFF pieces to detect stacks.
        // key = "color_cellType_positionIndex", value = list of slot indices at that position.
        Map<String, List<Integer>> posSlots = new HashMap<>();
        for (PlayerColor color : PlayerColor.ordered()) {
            Player player = gameState.getPlayer(color);
            if (player == null) continue;
            int slot = 0;
            for (Piece piece : player.getPieces()) {
                CellType ct = piece.getCellType();
                if (ct == CellType.OUTER || ct == CellType.TAKEOFF) {
                    String key = color.name() + "_" + ct.name() + "_" + piece.getPositionIndex();
                    posSlots.computeIfAbsent(key, k -> new ArrayList<>()).add(slot);
                }
                slot++;
            }
        }

        for (PlayerColor color : PlayerColor.ordered()) {
            Image img = pieceImages.get(color);
            if (img == null) continue;
            Player player = gameState.getPlayer(color);
            if (player == null) continue;
            int slot = 0;
            for (Piece piece : player.getPieces()) {
                int[] xy;
                boolean isWaiting = false;
                if (piece.getCellType() == CellType.WAITING_AREA) {
                    xy = birthPointSlotToPixel(colorToBirthIndex(color), slot);
                    isWaiting = true;
                } else if (piece.getCellType() == CellType.TAKEOFF) {
                    xy = takeoffIndexToPixel(piece.getPositionIndex());
                } else if (piece.getCellType() == CellType.OUTER) {
                    xy = outerIndexToPixel(piece.getPositionIndex(), 0, 0, 0, 0);
                } else {
                    slot++;
                    continue;
                }
                
                if (xy == null) { slot++; continue; }
                int x = xy[0] - size / 2, y = xy[1] - size / 2;

                // For stackable positions, only draw the image for the first piece in the stack.
                String key = color.name() + "_" + piece.getCellType().name() + "_" + piece.getPositionIndex();
                List<Integer> allSlots = posSlots.get(key);
                int stackCount = (allSlots != null) ? allSlots.size() : 1;
                boolean isFirstInStack = (allSlots == null) || (allSlots.get(0) == slot);

                boolean shouldDrawImage = isWaiting || isFirstInStack;
                if (shouldDrawImage) {
                    Shape oldClip = g2.getClip();
                    g2.setClip(new Ellipse2D.Double(x, y, size, size));
                    g2.drawImage(img, x, y, size, size, null);
                    g2.setClip(oldClip);

                    if (stackCount > 1) {
                        g2.setFont(getFont().deriveFont(Font.BOLD, size * 0.5f));
                        String label = "x" + stackCount;
                        FontMetrics fm = g2.getFontMetrics();
                        int labelW = fm.stringWidth(label);
                        int labelX = x + size - labelW / 2;
                        int labelY = y + fm.getAscent() / 3;
                        g2.setColor(new Color(0, 0, 0, 180));
                        g2.drawString(label, labelX + 1, labelY + 1);
                        g2.setColor(Color.WHITE);
                        g2.drawString(label, labelX, labelY);
                    }
                }

                // Hover highlight: for stacks, check if any slot in the stack is movable.
                boolean isAnyMovable;
                if (allSlots != null && allSlots.size() > 1) {
                    isAnyMovable = movable != null && color == lastDiceRollerColor
                        && allSlots.stream().anyMatch(movable::contains);
                } else {
                    isAnyMovable = movable != null && color == lastDiceRollerColor
                        && movable.contains(slot);
                }
                boolean isHovered = hoverX >= x && hoverX < x + size
                                    && hoverY >= y && hoverY < y + size;
                if (isAnyMovable && isHovered && shouldDrawImage) {
                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(2.5f));
                    int r = size / 2 + 3;
                    g2.drawOval(xy[0] - r, xy[1] - r, r * 2, r * 2);
                }
                slot++;
            }
        }
    }

    /** 步控模式下，用绿色大圈标出相邻可走格子（支持外圈、终点通道、中心格）。 */
    private void drawStepTargets(Graphics2D g2) {
        List<int[]> targets = getAdjacentForSelectedPiece();
        if (targets.isEmpty()) return;
        int r = (int) Math.max(14, cellLong * 0.4);
        g2.setColor(new Color(0x00, 0xCC, 0x00, 0xAA));
        g2.setStroke(new BasicStroke(3f));
        for (int[] tgt : targets) {
            int cellTypeOrdinal = tgt[0];
            int posIndex = tgt[1];
            int[] xy = stepTargetPixel(cellTypeOrdinal, posIndex);
            if (xy == null) continue;
            g2.drawOval(xy[0] - r, xy[1] - r, r * 2, r * 2);
            g2.setColor(new Color(0x00, 0xCC, 0x00, 0x30));
            g2.fillOval(xy[0] - r, xy[1] - r, r * 2, r * 2);
            g2.setColor(new Color(0x00, 0xCC, 0x00, 0xAA));
        }
    }

    /** 根据 cellType 和 positionIndex 计算步控目标像素坐标。 */
    private int[] stepTargetPixel(int cellTypeOrdinal, int posIndex) {
        if (cellTypeOrdinal == CellType.OUTER.ordinal()) {
            return outerIndexToPixel(posIndex, 0, 0, 0, 0);
        } else if (cellTypeOrdinal == CellType.CENTER_PATH.ordinal()) {
            PlayerColor c = com.flightchess.core.BoardConfig.decodeCenterPathColor(posIndex);
            int step = com.flightchess.core.BoardConfig.decodeCenterPathStep(posIndex);
            if (c == null || step < 0) return null;
            return finishLaneIndexToPixel(c, step);
        } else if (cellTypeOrdinal == CellType.CENTER.ordinal()) {
            return centerIndexToPixel(posIndex);
        }
        return null;
    }

    private void drawOuterTrack(Graphics2D g2, double left, double top, double right, double bottom) {
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double L = cellLong;
        double S = cellShort;
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

        double centerOffset = (L - S) / 2.0;

        // 黄色浅色长方形：放在上方，并相对三角形区域水平居中
        drawLightRect(g2, x2Left + centerOffset, y0 - S * 4.0 - L, S, L, PlayerColor.YELLOW);

        // 蓝色浅色长方形：放在右边，并相对三角形区域垂直居中
        drawLightRect(g2, x2Left + L, y0 - S * 4.0 + centerOffset, L, S, PlayerColor.BLUE);
        drawTrackRect(g2, x2Left - S, y0 - 2.0 * L, S, L, order[8 % 4]);
        drawTrackRect(g2, x2Left - 2.0 * S, y0 - 2.0 * L, S, L, order[9 % 4]);
        double x10Left = x2Left - 2.0 * L;
        drawRightTriangleCell(g2, x10Left, y0 - 2.0 * L, L, TriangleCorner.BOTTOM_RIGHT, order[10 % 4]);
        drawTrackRect(g2, x10Left, y0 - 2.0 * L - S, L, S, order[11 % 4]);
        drawTrackRect(g2, x10Left, y0 - 2.0 * L - S * 2.0, L, S, order[12 % 4]);

        // 画底部绿色终点通道：5 个 S×S 正方形 + 一个等腰三角形
        drawFinishLane(g2, x0, y0, S, 0, PlayerColor.GREEN);

        // 旋转生成另外三个方向的外圈轨道
        for (int q = 1; q <= 3; q++) {
            drawQuarterRotated(g2, cx, h, q, L, S, order);
        }

// 旋转生成另外三个方向的终点通道
        drawFinishLane(g2, x0, y0, S, 1, PlayerColor.RED);
        drawFinishLane(g2, x0, y0, S, 2, PlayerColor.BLUE);
        drawFinishLane(g2, x0, y0, S, 3, PlayerColor.YELLOW);
    }

    /**
     * Draw finish lane: 5 square cells above the rightmost vertical rectangle,
     * plus an isosceles triangle on top.
     *
     * @param baseX   x of the rightmost vertical outer-track rectangle
     * @param baseY   y of the rightmost vertical outer-track rectangle
     * @param S       short side length, also used as square side length
     * @param quarter 0=bottom, 1=left, 2=top, 3=right
     * @param color   lane color
     */
    private void drawFinishLane(Graphics2D g2, double baseX, double baseY, double S, int quarter, PlayerColor color) {
        double rotCx = quarterCenterX[quarter] >= 0 ? quarterCenterX[quarter] : getWidth() / 2.0;
        double rotCy = quarterCenterY[quarter] >= 0 ? quarterCenterY[quarter] : getHeight() / 2.0;

        // 5 个正方形，紧贴在右侧竖向长方形格子的上方
        for (int i = 1; i <= 5; i++) {
            double x = baseX;
            double y = baseY - i * S;
            drawRotatedRect(g2, x, y, S, S, rotCx, rotCy, quarter, color);
        }

        // 等腰三角形：
        // 底边 = 3S，高 = 1.5S
        // 底边贴在第 5 个正方形的上边
        double baseCenterX = baseX + S / 2.0;
        double baseLineY = baseY - 5.0 * S;
        double triangleBase = 3.0 * S;
        double triangleHeight = 1.5 * S;

        double x1 = baseCenterX - triangleBase / 2.0;
        double y1 = baseLineY;

        double x2 = baseCenterX + triangleBase / 2.0;
        double y2 = baseLineY;

        double x3 = baseCenterX;
        double y3 = baseLineY - triangleHeight;

        drawRotatedIsoscelesTriangle(g2, x1, y1, x2, y2, x3, y3, rotCx, rotCy, quarter, color);
    }

    /** Draw rotated isosceles triangle. */
    private void drawRotatedIsoscelesTriangle(
            Graphics2D g2,
            double x1, double y1,
            double x2, double y2,
            double x3, double y3,
            double rotCx, double rotCy,
            int quarter,
            PlayerColor color) {

        int steps = quarterToRotationSteps(quarter);

        double[] p1 = rotatePoint(x1, y1, rotCx, rotCy, steps);
        double[] p2 = rotatePoint(x2, y2, rotCx, rotCy, steps);
        double[] p3 = rotatePoint(x3, y3, rotCx, rotCy, steps);

        int[] xs = {
                (int) Math.round(p1[0]),
                (int) Math.round(p2[0]),
                (int) Math.round(p3[0])
        };

        int[] ys = {
                (int) Math.round(p1[1]),
                (int) Math.round(p2[1]),
                (int) Math.round(p3[1])
        };

        g2.setColor(colorFor(color));
        g2.fillPolygon(xs, ys, 3);

        g2.setColor(Color.WHITE);
        g2.drawPolygon(xs, ys, 3);
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

        double centerOffset = (L - S) / 2.0;

        drawRotatedLightRect(
                g2,
                x2Left + centerOffset,
                y0 - S * 4.0 - L,
                S,
                L,
                rotCx,
                rotCy,
                quarter,
                order[(baseIdx + 7) % 4]
        );

        drawRotatedLightRect(
                g2,
                x2Left + L,
                y0 - S * 4.0 + centerOffset,
                L,
                S,
                rotCx,
                rotCy,
                quarter,
                order[(baseIdx + 6) % 4]
        );
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

    /** Draw rotated light rectangle. */
    private void drawRotatedLightRect(
            Graphics2D g2,
            double x, double y,
            double w, double h,
            double cx, double cy,
            int quarter,
            PlayerColor color) {

        int steps = quarterToRotationSteps(quarter);

        double centerX = x + w / 2.0;
        double centerY = y + h / 2.0;

        double[] c = rotatePoint(centerX, centerY, cx, cy, steps);

        double nw = (steps % 2 == 0) ? w : h;
        double nh = (steps % 2 == 0) ? h : w;

        drawLightRect(g2, c[0] - nw / 2.0, c[1] - nh / 2.0, nw, nh, color);
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
    /** Draw a light-colored rectangle cell without center dot. */
    private void drawLightRect(Graphics2D g2, double x, double y, double w, double h, PlayerColor color) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int iw = (int) Math.round(w);
        int ih = (int) Math.round(h);

        g2.setColor(lightColorFor(color));
        g2.fillRect(ix, iy, iw, ih);

        g2.setColor(Color.WHITE);
        g2.drawRect(ix, iy, iw, ih);
    }

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
        int side = (int) Math.round(cellLong * 2.0);
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

    //public void TestDrawOuterCells

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
        double L = cellLong;
        double S = cellShort;
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

    /** 终点通道格子像素坐标：laneIdx = 0~4，0 最外（近外圈），4 最内（近中心）。 */
    private int[] finishLaneIndexToPixel(PlayerColor color, int laneIdx) {
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double L = cellLong;
        double S = cellShort;
        double y0 = h - L;
        // 未旋转时的中心坐标：终点通道第 laneIdx 格的正中心（laneIdx=0 对应 drawing cell 1）
        double bx = cx;
        double by = y0 - laneIdx * S - 0.5 * S;
        PlayerColor[] order = PlayerColor.ordered();
        int quarter = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == color) { quarter = i; break; }
        }
        if (quarter <= 0) {
            return new int[]{ (int) Math.round(bx), (int) Math.round(by) };
        }
        double rotCx = quarterCenterX[quarter] >= 0 ? quarterCenterX[quarter] : cx;
        double rotCy = quarterCenterY[quarter] >= 0 ? quarterCenterY[quarter] : h / 2.0;
        int steps = quarterToRotationSteps(quarter);
        double[] r = rotatePoint(bx, by, rotCx, rotCy, steps);
        return new int[]{ (int) Math.round(r[0]), (int) Math.round(r[1]) };
    }

    /** 中心四格像素坐标：centerIdx = 0(GREEN) / 1(RED) / 2(BLUE) / 3(YELLOW)。 */
    private int[] centerIndexToPixel(int centerIdx) {
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double L = cellLong;
        double S = cellShort;
        double y0 = h - L;
        // 未旋转时的中心格位置：终点通道三角形尖端 (cx, y0 - 6.5*S)
        double bx = cx;
        double by = y0 - 6.5 * S;
        if (centerIdx <= 0) {
            return new int[]{ (int) Math.round(bx), (int) Math.round(by) };
        }
        double rotCx = cx;
        double rotCy = h / 2.0;
        int steps = centerIdx; // 每色旋转 N×90°
        double[] r = rotatePoint(bx, by, rotCx, rotCy, steps);
        return new int[]{ (int) Math.round(r[0]), (int) Math.round(r[1]) };
    }

    /** Convert outer-track index to cell center pixel, matching drawOuterTrack layout. */
    private int[] outerIndexToPixel(int index, int left, int top, int right, int bottom) {
        if (index < 0 || index >= 52) {
            return new int[] { (int) Math.round(getWidth() / 2.0), (int) Math.round(getHeight() / 2.0) };
        }
        double cx = getWidth() / 2.0;
        double h = getHeight();
        double L = cellLong;
        double S = cellShort;
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
    /** Lighter color for center helper rectangles. */
    private Color lightColorFor(PlayerColor c) {
        switch (c) {
            case GREEN:
                return new Color(0xA5, 0xD6, 0xA7); // light green
            case RED:
                return new Color(0xEF, 0x9A, 0x9A); // light red
            case BLUE:
                return new Color(0x90, 0xCA, 0xF9); // light blue
            case YELLOW:
                return new Color(0xFF, 0xF5, 0x9D); // light yellow
            default:
                return Color.LIGHT_GRAY;
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
