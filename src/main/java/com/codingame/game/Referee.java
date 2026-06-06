package com.codingame.game;

import java.util.List;

import com.codingame.gameengine.core.AbstractPlayer.TimeoutException;
import com.codingame.gameengine.core.AbstractReferee;
import com.codingame.gameengine.core.MultiplayerGameManager;
import com.codingame.gameengine.module.endscreen.EndScreenModule;
import com.codingame.gameengine.module.entities.Circle;
import com.codingame.gameengine.module.entities.Curve;
import com.codingame.gameengine.module.entities.GraphicEntityModule;
import com.codingame.gameengine.module.entities.Group;
import com.codingame.gameengine.module.entities.Sprite;
import com.codingame.gameengine.module.entities.Text;
import com.codingame.gameengine.module.toggle.ToggleModule;
import com.google.inject.Inject;

public class Referee extends AbstractReferee {

    // ── Layout ──────────────────────────────────────────────────────────────
    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;
    private static final int CELL      = 90;
    private static final int GRID_W    = Board.SIZE * CELL;
    private static final int GRID_X    = (SCREEN_W - GRID_W) / 2;
    private static final int GRID_Y    = (SCREEN_H - GRID_W) / 2;

    // ── Colours ─────────────────────────────────────────────────────────────
    private static final int COL_BG    = 0x0d0d1a;
    private static final int COL_GRID  = 0x1e1e3a;
    private static final int COL_WHITE = 0xFFFFFF;

    // ── Dot offsets (relative to cell centre) ───────────────────────────────
    private static final int[][] DOT_POS_1 = {{0, 0}};
    private static final int[][] DOT_POS_2 = {{-18, 0}, {18, 0}};
    private static final int[][] DOT_POS_3 = {{0, -20}, {-18, 14}, {18, 14}};
    private static final int[][][] DOT_POS = {null, DOT_POS_1, DOT_POS_2, DOT_POS_3};
    private static final int DOT_RADIUS = 10;

    @Inject private MultiplayerGameManager<Player> gameManager;
    @Inject private GraphicEntityModule gem;
    @Inject private ToggleModule toggleModule;
    @Inject private EndScreenModule endScreenModule;

    private Board board;

    // Visual entities per cell: 3 dots pre-created, shown/hidden per count
    private Circle[][][] dots;   // dots[r][c][d] – up to 3 dots
    private Circle[][] glow;     // outer glow circle per cell

    // Pool of flying orbs used during explosion animations
    private static final int FLYING_POOL_SIZE = 0;
    private Circle[] flyingOrbs;
    private int flyingOrbIdx;

    private int[] playerColors;
    private Text[] scoreTexts;
    private int turnCount = 0;
    private boolean[] colorSent = {false, false};

    // ── Init ────────────────────────────────────────────────────────────────
    @Override
    public void init() {
        board = new Board();
        playerColors = gameManager.getPlayers().stream()
            .mapToInt(Player::getColorToken).toArray();
        gameManager.setFirstTurnMaxTime(1000);
        gameManager.setTurnMaxTime(100);
        gameManager.setMaxTurns(200);
        gameManager.setFrameDuration(800);

        drawBackground();
        drawGrid();
        drawHud();
        initCellEntities();
        placeStartingOrbs();
    }

    private void drawBackground() {
        gem.createRectangle()
            .setWidth(SCREEN_W).setHeight(SCREEN_H)
            .setX(0).setY(0)
            .setFillColor(COL_BG)
            .setLineWidth(0)
            .setZIndex(0);
    }

    private void drawGrid() {
        int total = Board.SIZE * CELL;
        for (int i = 0; i <= Board.SIZE; i++) {
            int y = GRID_Y + i * CELL;
            gem.createLine()
                .setX(GRID_X).setY(y)
                .setX2(GRID_X + total).setY2(y)
                .setLineColor(COL_GRID).setLineWidth(2)
                .setZIndex(1);
        }
        for (int i = 0; i <= Board.SIZE; i++) {
            int x = GRID_X + i * CELL;
            gem.createLine()
                .setX(x).setY(GRID_Y)
                .setX2(x).setY2(GRID_Y + total)
                .setLineColor(COL_GRID).setLineWidth(2)
                .setZIndex(1);
        }
    }

    private void drawHud() {
        scoreTexts = new Text[2];
        List<Player> players = gameManager.getPlayers();
        int[] xs = {200, SCREEN_W - 200};

        for (int i = 0; i < 2; i++) {
            Player p = players.get(i);
            int x = xs[i];
            int color = playerColors[i];

            gem.createRectangle()
                .setWidth(300).setHeight(90)
                .setX(x - 150).setY(30)
                .setFillColor(color).setAlpha(0.15)
                .setLineColor(color).setLineWidth(2)
                .setZIndex(2);

            gem.createSprite()
                .setImage(p.getAvatarToken())
                .setX(x - 100).setY(75)
                .setAnchor(0.5)
                .setBaseWidth(70).setBaseHeight(70)
                .setZIndex(3);

            gem.createText(p.getNicknameToken())
                .setX(x + 10).setY(55)
                .setAnchor(0.5)
                .setFontSize(28)
                .setFillColor(color)
                .setZIndex(3);

            scoreTexts[i] = gem.createText("0 orbs")
                .setX(x + 10).setY(85)
                .setAnchor(0.5)
                .setFontSize(22)
                .setFillColor(COL_WHITE)
                .setZIndex(3);

            p.hud = gem.createGroup();
        }
    }

    private void initCellEntities() {
        dots = new Circle[Board.SIZE][Board.SIZE][3];
        glow = new Circle[Board.SIZE][Board.SIZE];

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                int cx = GRID_X + c * CELL + CELL / 2;
                int cy = GRID_Y + r * CELL + CELL / 2;

                glow[r][c] = gem.createCircle()
                    .setRadius(38)
                    .setX(cx).setY(cy)
                    .setFillColor(COL_WHITE).setAlpha(0)
                    .setLineWidth(0).setZIndex(3);

                for (int d = 0; d < 3; d++) {
                    dots[r][c][d] = gem.createCircle()
                        .setRadius(DOT_RADIUS)
                        .setX(cx).setY(cy)
                        .setFillColor(COL_WHITE)
                        .setLineWidth(0)
                        .setScale(0).setAlpha(0)
                        .setZIndex(5);
                }
            }
        }

        flyingOrbs = new Circle[FLYING_POOL_SIZE];
        for (int i = 0; i < FLYING_POOL_SIZE; i++) {
            flyingOrbs[i] = gem.createCircle()
                .setRadius(DOT_RADIUS)
                .setFillColor(COL_WHITE)
                .setLineWidth(0)
                .setAlpha(0)
                .setZIndex(10);
        }
    }

    private void placeStartingOrbs() {
        board.getCell(0, 0).owner = 1;
        board.getCell(0, 0).orbs  = 1;
        board.getCell(7, 7).owner = 2;
        board.getCell(7, 7).orbs  = 1;
        syncCell(0, 0);
        syncCell(7, 7);
    }

    // ── Rendering helpers ───────────────────────────────────────────────────

    private int playerColor(int owner) {
        return playerColors[owner - 1];
    }

    private void syncCellAt(int r, int c, double t) {
        Cell cell = board.getCell(r, c);
        int owner = cell.owner;
        int count = cell.orbs;
        int cx = GRID_X + c * CELL + CELL / 2;
        int cy = GRID_Y + r * CELL + CELL / 2;

        if (owner == 0 || count == 0) {
            glow[r][c].setAlpha(0);
            gem.commitEntityState(t, glow[r][c]);
            for (int d = 0; d < 3; d++) {
                dots[r][c][d].setScale(0).setAlpha(0);
                gem.commitEntityState(t, dots[r][c][d]);
            }
            return;
        }

        int displayCount = Math.min(count, DOT_POS.length - 1);
        int[][] positions = DOT_POS[displayCount];

        glow[r][c].setFillColor(playerColor(owner)).setAlpha(0.85);
        gem.commitEntityState(t, glow[r][c]);

        for (int d = 0; d < 3; d++) {
            Circle dot = dots[r][c][d];
            if (d < displayCount) {
                dot.setX(cx + positions[d][0]).setY(cy + positions[d][1])
                   .setFillColor(COL_WHITE).setScale(1).setAlpha(1);
            } else {
                dot.setScale(0).setAlpha(0);
            }
            gem.commitEntityState(t, dot);
        }
    }

    private void syncCell(int r, int c) {
        Cell cell = board.getCell(r, c);
        int owner = cell.owner;
        int count = cell.orbs;

        if (owner == 0 || count == 0) {
            glow[r][c].setAlpha(0);
            for (int d = 0; d < 3; d++) dots[r][c][d].setScale(0).setAlpha(0);
            return;
        }

        int displayCount = Math.min(count, DOT_POS.length - 1);
        int[][] positions = DOT_POS[displayCount];
        int cx = GRID_X + c * CELL + CELL / 2;
        int cy = GRID_Y + r * CELL + CELL / 2;

        glow[r][c].setFillColor(playerColor(owner)).setAlpha(0.85);
        for (int d = 0; d < 3; d++) {
            if (d < displayCount) {
                dots[r][c][d]
                    .setX(cx + positions[d][0]).setY(cy + positions[d][1])
                    .setFillColor(COL_WHITE).setScale(1).setAlpha(1);
            } else {
                dots[r][c][d].setScale(0).setAlpha(0);
            }
        }
    }

    private void animateCellAt(int r, int c, int oldCount, int oldOwner,
                                double tAppear, double tSettle) {
        Cell cell = board.getCell(r, c);
        int owner = cell.owner;
        int count = cell.orbs;
        int cx = GRID_X + c * CELL + CELL / 2;
        int cy = GRID_Y + r * CELL + CELL / 2;

        if (owner == 0 || count == 0) {
            glow[r][c].setAlpha(0);
            gem.commitEntityState(tSettle, glow[r][c]);
            for (int d = 0; d < 3; d++) {
                dots[r][c][d].setScale(0).setAlpha(0);
                gem.commitEntityState(tSettle, dots[r][c][d]);
            }
            return;
        }

        int displayCount = Math.min(count, DOT_POS.length - 1);
        int[][] positions = DOT_POS[displayCount];

        glow[r][c].setFillColor(playerColor(owner));
        glow[r][c].setAlpha(0.85, Curve.EASE_IN);
        gem.commitEntityState(tSettle, glow[r][c]);

        for (int d = 0; d < 3; d++) {
            Circle dot = dots[r][c][d];
            if (d < displayCount) {
                dot.setX(cx + positions[d][0])
                   .setY(cy + positions[d][1])
                   .setFillColor(COL_WHITE);
                dot.setScale(0).setAlpha(1);
                gem.commitEntityState(tAppear, dot);
                dot.setScale(1.2, Curve.EASE_OUT);
                gem.commitEntityState(tAppear + (tSettle - tAppear) * 0.6, dot);
                dot.setScale(1, Curve.EASE_IN);
                gem.commitEntityState(tSettle, dot);
            } else {
                dot.setScale(0).setAlpha(0);
                gem.commitEntityState(tSettle, dot);
            }
        }
    }

    private void animateFlyingOrb(int fromR, int fromC, int toR, int toC,
                                   double tStart, double tEnd) {
        if (flyingOrbIdx >= FLYING_POOL_SIZE) return;
        Circle orb = flyingOrbs[flyingOrbIdx++];
        int srcX = GRID_X + fromC * CELL + CELL / 2;
        int srcY = GRID_Y + fromR * CELL + CELL / 2;
        int dstX = GRID_X + toC   * CELL + CELL / 2;
        int dstY = GRID_Y + toR   * CELL + CELL / 2;
        orb.setX(srcX).setY(srcY).setAlpha(0);
        gem.commitEntityState(Math.max(0.0, tStart - 0.001), orb);
        orb.setAlpha(1);
        gem.commitEntityState(tStart, orb);
        orb.setX(dstX, Curve.EASE_IN_AND_OUT)
           .setY(dstY, Curve.EASE_IN_AND_OUT)
           .setAlpha(0, Curve.EASE_IN);
        gem.commitEntityState(tEnd, orb);
    }

    private void updateScores() {
        scoreTexts[0].setText(board.countOrbs(1) + " orbs");
        scoreTexts[1].setText(board.countOrbs(2) + " orbs");
    }

    // ── Game turn ───────────────────────────────────────────────────────────

    @Override
    public void gameTurn(int turn) {
        turnCount++;
        Player player = gameManager.getPlayer(turn % 2);
        int playerIdx = player.getIndex() + 1;

        sendGameState(player, playerIdx);
        player.execute();

        try {
            Action action = player.getAction();
            gameManager.addToGameSummary(String.format(
                "%s plays (%d %d)", player.getNicknameToken(), action.row, action.col));

            // Snapshot before move
            int[][] snapOwner = new int[Board.SIZE][Board.SIZE];
            int[][] snapOrbs  = new int[Board.SIZE][Board.SIZE];
            for (int r = 0; r < Board.SIZE; r++)
                for (int c = 0; c < Board.SIZE; c++) {
                    snapOwner[r][c] = board.getCell(r, c).owner;
                    snapOrbs[r][c]  = board.getCell(r, c).orbs;
                }

            List<int[]> explosions = board.play(action.row, action.col, playerIdx);
            flyingOrbIdx = 0;

            // t=0.00–0.25 : placement
            animateCellAt(action.row, action.col,
                snapOrbs[action.row][action.col],
                snapOwner[action.row][action.col],
                0.0, 0.25);

            // t=0.25–0.75 : first few explosions
            final int MAX_ANIM = 6;
            int animCount = Math.min(explosions.size(), MAX_ANIM);
            if (animCount > 0) {
                double slice = 0.5 / animCount;
                int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                for (int i = 0; i < animCount; i++) {
                    int er = explosions.get(i)[0];
                    int ec = explosions.get(i)[1];
                    double t0   = 0.25 + i * slice;
                    double tMid = t0 + slice * 0.45;
                    double t1   = t0 + slice;
                    animateCellAt(er, ec, snapOrbs[er][ec], snapOwner[er][ec], t0, tMid);
                    for (int[] d : dirs) {
                        int nr = er + d[0], nc = ec + d[1];
                        if (board.isValid(nr, nc)) {
                            animateFlyingOrb(er, ec, nr, nc, t0, tMid);
                            animateCellAt(nr, nc, snapOrbs[nr][nc], snapOwner[nr][nc], tMid, t1);
                        }
                    }
                }
            }

            // t=1.0 : final state for all changed cells
            for (int r = 0; r < Board.SIZE; r++)
                for (int c = 0; c < Board.SIZE; c++) {
                    Cell cell = board.getCell(r, c);
                    if (cell.owner != snapOwner[r][c] || cell.orbs != snapOrbs[r][c])
                        syncCellAt(r, c, 1.0);
                }

            updateScores();
            gem.commitEntityState(1.0, scoreTexts[0]);
            gem.commitEntityState(1.0, scoreTexts[1]);

            if (turnCount >= 2) {
                int opponent = (playerIdx == 1) ? 2 : 1;
                if (board.countOrbs(opponent) == 0) {
                    player.setScore(board.countOrbs(playerIdx));
                    gameManager.addToGameSummary(player.getNicknameToken() + " wins!");
                    gameManager.endGame();
                    return;
                }
            }

        } catch (TimeoutException e) {
            player.deactivate(player.getNicknameToken() + " timed out!");
            player.setScore(-1);
            gameManager.endGame();
        } catch (InvalidAction e) {
            player.deactivate(e.getMessage());
            player.setScore(-1);
            gameManager.endGame();
        } catch (NumberFormatException e) {
            player.deactivate("Invalid output format!");
            player.setScore(-1);
            gameManager.endGame();
        }
    }

    // ── Input to bots ────────────────────────────────────────────────────────

    private void sendGameState(Player player, int playerIdx) {
        int pIdx = playerIdx - 1;
        if (!colorSent[pIdx]) {
            player.sendInputLine(playerIdx == 1 ? "r" : "b");
            colorSent[pIdx] = true;
        }

        player.sendInputLine(board.countOrbs(1) + " " + board.countOrbs(2));

        for (int r = 0; r < Board.SIZE; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < Board.SIZE; c++) {
                if (c > 0) sb.append(' ');
                Cell cell = board.getCell(r, c);
                if (cell.owner == 0)      sb.append('.');
                else if (cell.owner == 1) sb.append('r').append(cell.orbs);
                else                      sb.append('b').append(cell.orbs);
            }
            player.sendInputLine(sb.toString());
        }
    }
}
