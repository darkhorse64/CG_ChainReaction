package com.codingame.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    private static final int[][] DOT_POS_4 = {{-15, -15}, {15, -15}, {-15, 15}, {15, 15}};
    private static final int[][][] DOT_POS = {null, DOT_POS_1, DOT_POS_2, DOT_POS_3, DOT_POS_4};
    private static final int DOT_RADIUS = 10;

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    @Inject private MultiplayerGameManager<Player> gameManager;
    @Inject private GraphicEntityModule gem;
    @Inject private ToggleModule toggleModule;
    @Inject private EndScreenModule endScreenModule;

    private Board board;

    private Group[][]   cellGroups; // one Group per cell, positioned at cell centre
    private Circle[][][] dots;      // dots[r][c][d] – up to 4 dots, coords relative to group
    private Circle[][] glow;        // colored container per cell, coords relative to group

    private static final int FLYING_POOL_SIZE = 0;
    private Circle[] flyingOrbs;
    private int flyingOrbIdx;

    private int[] playerColors;
    private Text[] scoreTexts;
    private Text[] messageTexts;
    private boolean[] colorSent = {false, false};
    private int[][] lastMove = {null, null}; // lastMove[playerIdx-1] = {row, col}
    private Random random;

    // ── Wave-frame queue ─────────────────────────────────────────────────────
    /**
     * One pending animation frame per explosion wave.
     * Each WaveFrame contains the board state just BEFORE that wave fires,
     * so we can render intermediate states correctly even though board.play()
     * has already applied the full chain to the board.
     */
    private static class WaveFrame {
        final List<int[]> wave;
        final int[][] vOrbs;    // board state at the START of this wave
        final int[][] vOwner;
        final int playerIdx;

        WaveFrame(List<int[]> wave, int[][] vOrbs, int[][] vOwner, int playerIdx) {
            this.wave     = wave;
            this.vOrbs    = vOrbs;
            this.vOwner   = vOwner;
            this.playerIdx = playerIdx;
        }
    }

    private final ArrayDeque<WaveFrame> pendingFrames = new ArrayDeque<>();
    private int  currentPlayerTurn = 0;   // 0 or 1
    private int  playerMoveCount   = 0;
    private int  pendingWinnerIdx  = 0;   // set when a win is detected mid-chain
    private boolean pendingEndGame = false;

    // ── Init ────────────────────────────────────────────────────────────────
    @Override
    public void init() {
        board = new Board();
        playerColors = gameManager.getPlayers().stream()
            .mapToInt(Player::getColorToken).toArray();
        gameManager.setFirstTurnMaxTime(1000);
        gameManager.setTurnMaxTime(100);
        // Extra turns budget: 200 player moves × up to 15 wave frames each
        gameManager.setMaxTurns(200 * 16);
        gameManager.setFrameDuration(500);

        random = new Random(gameManager.getSeed());

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
        scoreTexts  = new Text[2];
        messageTexts = new Text[2];
        List<Player> players = gameManager.getPlayers();
        int[] xs = {200, SCREEN_W - 200};

        for (int i = 0; i < 2; i++) {
            Player p = players.get(i);
            int x = xs[i];
            int color = playerColors[i];

            gem.createRectangle()
                .setWidth(300).setHeight(115)
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

            scoreTexts[i] = gem.createText("0 cells")
                .setX(x + 10).setY(85)
                .setAnchor(0.5)
                .setFontSize(22)
                .setFillColor(COL_WHITE)
                .setZIndex(3);

            messageTexts[i] = gem.createText("")
                .setX(x).setY(122)
                .setAnchor(0.5)
                .setFontSize(18)
                .setFillColor(COL_WHITE)
                .setAlpha(0.85)
                .setZIndex(3);

            p.hud = gem.createGroup();
        }
    }

    private void initCellEntities() {
        cellGroups = new Group[Board.SIZE][Board.SIZE];
        dots = new Circle[Board.SIZE][Board.SIZE][4];
        glow = new Circle[Board.SIZE][Board.SIZE];

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                int cx = GRID_X + c * CELL + CELL / 2;
                int cy = GRID_Y + r * CELL + CELL / 2;

                // All child entities positioned at (0,0) relative to the group
                glow[r][c] = gem.createCircle()
                    .setRadius(38)
                    .setX(0).setY(0)
                    .setFillColor(COL_WHITE).setAlpha(0)
                    .setLineWidth(0).setZIndex(0);

                for (int d = 0; d < 4; d++) {
                    dots[r][c][d] = gem.createCircle()
                        .setX(0).setY(0)
                        .setFillColor(COL_WHITE)
                        .setLineWidth(0)
                        .setRadius(0).setAlpha(0)
                        .setZIndex(1);
                }

                cellGroups[r][c] = gem.createGroup(
                    glow[r][c],
                    dots[r][c][0], dots[r][c][1], dots[r][c][2], dots[r][c][3]
                ).setX(cx).setY(cy).setZIndex(3);
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
        board.getCell(Board.SIZE - 1, Board.SIZE - 1).owner = 2;
        board.getCell(Board.SIZE - 1, Board.SIZE - 1).orbs  = 1;
        syncCell(0, 0);
        syncCell(Board.SIZE - 1, Board.SIZE - 1);
    }

    // ── Rendering helpers ───────────────────────────────────────────────────

    private int playerColor(int owner) {
        return playerColors[owner - 1];
    }

    /** Commit the visual for a cell given explicit state at time t. */
    private void commitCellVisual(int r, int c, int orbs, int owner, double t) {
        double tt = roundToThirdDecimal(t);

        if (owner == 0 || orbs == 0) {
            glow[r][c].setAlpha(0);
            gem.commitEntityState(tt, glow[r][c]);
            for (int d = 0; d < 4; d++) {
                dots[r][c][d].setRadius(0).setAlpha(0);
                gem.commitEntityState(tt, dots[r][c][d]);
            }
            return;
        }

        int displayCount = Math.min(orbs, DOT_POS.length - 1);
        int[][] positions = DOT_POS[displayCount];

        glow[r][c].setFillColor(playerColor(owner)).setAlpha(0.85);
        gem.commitEntityState(tt, glow[r][c]);

        for (int d = 0; d < 4; d++) {
            Circle dot = dots[r][c][d];
            if (d < displayCount) {
                dot.setX(positions[d][0]).setY(positions[d][1])
                   .setFillColor(COL_WHITE).setRadius(DOT_RADIUS).setAlpha(1);
            } else {
                dot.setRadius(0).setAlpha(0);
            }
            gem.commitEntityState(tt, dot);
        }
    }

    /** Read board state and commit at time t (used for final state sync). */
    private void syncCellAt(int r, int c, double t) {
        Cell cell = board.getCell(r, c);
        commitCellVisual(r, c, cell.orbs, cell.owner, t);
    }

    /** Set entity properties without committing (used during init). */
    private void syncCell(int r, int c) {
        Cell cell = board.getCell(r, c);
        int owner = cell.owner;
        int count = cell.orbs;

        if (owner == 0 || count == 0) {
            glow[r][c].setAlpha(0);
            for (int d = 0; d < 4; d++) dots[r][c][d].setRadius(0).setAlpha(0);
            return;
        }

        int displayCount = Math.min(count, DOT_POS.length - 1);
        int[][] positions = DOT_POS[displayCount];

        glow[r][c].setFillColor(playerColor(owner)).setAlpha(0.85);
        for (int d = 0; d < 4; d++) {
            if (d < displayCount) {
                dots[r][c][d]
                    .setX(positions[d][0]).setY(positions[d][1])
                    .setFillColor(COL_WHITE).setRadius(DOT_RADIUS).setAlpha(1);
            } else {
                dots[r][c][d].setRadius(0).setAlpha(0);
            }
        }
    }

    /** Bounce-in animation for a cell (placement). */
    private void animatePlacementCell(int r, int c, int oldOrbs, int oldOwner,
                                      int newOrbs, int newOwner,
                                      double tAppear, double tSettle) {
        int displayCount = Math.min(newOrbs, DOT_POS.length - 1);
        int[][] positions = DOT_POS[displayCount];

        glow[r][c].setFillColor(playerColor(newOwner)).setAlpha(0.85, Curve.EASE_IN);
        gem.commitEntityState(roundToThirdDecimal(tSettle), glow[r][c]);

        for (int d = 0; d < 4; d++) {
            Circle dot = dots[r][c][d];
            if (d < displayCount) {
                dot.setX(positions[d][0]).setY(positions[d][1]).setFillColor(COL_WHITE);
                dot.setRadius(0).setAlpha(1);
                gem.commitEntityState(roundToThirdDecimal(tAppear), dot);
                dot.setRadius((int)(DOT_RADIUS * 1.2), Curve.EASE_OUT);
                gem.commitEntityState(roundToThirdDecimal(tAppear + (tSettle - tAppear) * 0.6), dot);
                dot.setRadius(DOT_RADIUS, Curve.EASE_IN);
                gem.commitEntityState(roundToThirdDecimal(tSettle), dot);
            } else {
                dot.setRadius(0).setAlpha(0);
                gem.commitEntityState(roundToThirdDecimal(tSettle), dot);
            }
        }
    }

    /** Animate one orb flying from cell (fromR,fromC) to (toR,toC). */
    private void animateFlyingOrb(int fromR, int fromC, int toR, int toC,
                                   double tStart, double tEnd) {
        if (flyingOrbIdx >= FLYING_POOL_SIZE) return;
        Circle orb = flyingOrbs[flyingOrbIdx++];
        int srcX = GRID_X + fromC * CELL + CELL / 2;
        int srcY = GRID_Y + fromR * CELL + CELL / 2;
        int dstX = GRID_X + toC   * CELL + CELL / 2;
        int dstY = GRID_Y + toR   * CELL + CELL / 2;
        orb.setX(srcX).setY(srcY).setAlpha(0);
        gem.commitEntityState(roundToThirdDecimal(Math.max(0.0, tStart - 0.001)), orb);
        orb.setAlpha(1);
        gem.commitEntityState(roundToThirdDecimal(tStart), orb);
        orb.setX(dstX, Curve.EASE_IN_AND_OUT).setY(dstY, Curve.EASE_IN_AND_OUT)
           .setAlpha(0, Curve.EASE_IN);
        gem.commitEntityState(roundToThirdDecimal(tEnd), orb);
    }

    private void updateScores() {
        updateScores(board.countCells(1), board.countCells(2));
    }

    private void updateScores(int cells1, int cells2) {
        scoreTexts[0].setText(cells1 + " cells");
        scoreTexts[1].setText(cells2 + " cells");
        gem.commitEntityState(roundToThirdDecimal(1.0), scoreTexts[0]);
        gem.commitEntityState(roundToThirdDecimal(1.0), scoreTexts[1]);
    }

    /** Round a time value to 3 decimal places to reduce replay data size. */
    private static double roundToThirdDecimal(double t) {
        return Math.round(t * 1000) / 1000.0;
    }

    // ── Game turn ───────────────────────────────────────────────────────────

    @Override
    public void gameTurn(int turn) {
        flyingOrbIdx = 0;

        // ── Wave animation frame ─────────────────────────────────────────────
        if (!pendingFrames.isEmpty()) {
            WaveFrame wf = pendingFrames.poll();
            animateWaveFrame(wf);  // updateScores() called inside with intermediate state

            if (pendingFrames.isEmpty()) {
                if (pendingEndGame) {
                    if (pendingWinnerIdx > 0) {
                        Player winner = gameManager.getPlayer(pendingWinnerIdx - 1);
                        winner.setScore(board.countCells(pendingWinnerIdx));
                        gameManager.addToGameSummary(winner.getNicknameToken() + " wins!");
                    }
                    gameManager.endGame();
                }
            }
            return;
        }

        // ── Real player turn ─────────────────────────────────────────────────
        Player player = gameManager.getPlayer(currentPlayerTurn);
        int playerIdx = currentPlayerTurn + 1; // 1 or 2

        sendGameState(player, playerIdx);
        player.execute();

        try {
            Action action;
            String rawOutput = player.getOutputs().get(0).trim();
            String playerMessage = "";
            if (rawOutput.equalsIgnoreCase("random")) {
                action = randomAction(player, playerIdx);
            } else {
                action = player.getAction();
                String[] parts = rawOutput.split("\\s+", 3);
                if (parts.length >= 3) playerMessage = parts[2];
            }

            // Update message in HUD: show current player's message, clear opponent's
            int hudIdx = currentPlayerTurn;
            String displayMessage = playerMessage.length() > 20
                ? playerMessage.substring(0, 20) : playerMessage;
            messageTexts[hudIdx].setText(displayMessage);
            gem.commitEntityState(0.0, messageTexts[hudIdx]);
            messageTexts[1 - hudIdx].setText("");
            gem.commitEntityState(0.0, messageTexts[1 - hudIdx]);

            // Snapshot before move
            int[][] snapOwner = new int[Board.SIZE][Board.SIZE];
            int[][] snapOrbs  = new int[Board.SIZE][Board.SIZE];
            for (int r = 0; r < Board.SIZE; r++)
                for (int c = 0; c < Board.SIZE; c++) {
                    snapOwner[r][c] = board.getCell(r, c).owner;
                    snapOrbs[r][c]  = board.getCell(r, c).orbs;
                }

            // Apply move — board is now in final state, waves returned in BFS order
            List<List<int[]>> waves = board.play(action.row, action.col, playerIdx);
            lastMove[currentPlayerTurn] = new int[]{action.row, action.col};

            // Game summary
            String randomTag = rawOutput.equalsIgnoreCase("random") ? " random →" : "";
            int waveCount = waves.size();
            String explosionTag = waveCount > 0
                ? String.format(" and triggered %d explosion wave(s)", waveCount)
                : "";
            gameManager.addToGameSummary(String.format(
                "At game turn %d, %s played%s (%d %d)%s",
                playerMoveCount + 1, player.getNicknameToken(), randomTag, action.row, action.col, explosionTag));

            // Build virtual state to track intermediate board states for animation
            int[][] vOrbs  = copyGrid(snapOrbs);
            int[][] vOwner = copyGrid(snapOwner);
            vOrbs[action.row][action.col]++;
            vOwner[action.row][action.col] = playerIdx;

            // ── Placement frame (current frame) ──────────────────────────────
            // Show the orb being placed. If it immediately reaches critical mass,
            // the cell will display e.g. 2/3/4 orbs — visually "tense" before the
            // next frame explodes it.
            animatePlacementCell(
                action.row, action.col,
                snapOrbs[action.row][action.col], snapOwner[action.row][action.col],
                vOrbs[action.row][action.col],    vOwner[action.row][action.col],
                0.1, 0.6);

            // Commit the final "placed" state at t=1.0 (holds until next frame)
            commitCellVisual(action.row, action.col,
                vOrbs[action.row][action.col], vOwner[action.row][action.col], 1.0);

            // ── Queue one WaveFrame per explosion wave ────────────────────────
            // Pre-compute the virtual board state at the START of each wave.
            for (List<int[]> wave : waves) {
                // Take snapshot of vState at start of this wave (before explosion)
                pendingFrames.add(new WaveFrame(wave, copyGrid(vOrbs), copyGrid(vOwner), playerIdx));

                // Advance vState: cells explode → empty, neighbors receive +1
                for (int[] exp : wave) {
                    vOrbs[exp[0]][exp[1]]  = 0;
                    vOwner[exp[0]][exp[1]] = 0;
                }
                for (int[] exp : wave)
                    for (int[] d : DIRS) {
                        int nr = exp[0] + d[0], nc = exp[1] + d[1];
                        if (board.isValid(nr, nc)) {
                            vOrbs[nr][nc]++;
                            vOwner[nr][nc] = playerIdx;
                        }
                    }
            }

            // Update scores display — use pre-explosion snapshot so the placement
            // frame shows the score before any chain reaction fires.
            int snapCells1 = 0, snapCells2 = 0;
            for (int r = 0; r < Board.SIZE; r++)
                for (int c = 0; c < Board.SIZE; c++) {
                    if (snapOwner[r][c] == 1) snapCells1++;
                    else if (snapOwner[r][c] == 2) snapCells2++;
                }
            updateScores(snapCells1, snapCells2);

            // ── Win check ────────────────────────────────────────────────────
            playerMoveCount++;
            if (playerMoveCount >= 2) {
                int opponent = (playerIdx == 1) ? 2 : 1;
                if (board.countCells(opponent) == 0) {
                    pendingWinnerIdx = playerIdx;
                    pendingEndGame   = true;
                    if (pendingFrames.isEmpty()) {
                        Player winner = gameManager.getPlayer(playerIdx - 1);
                        winner.setScore(board.countCells(playerIdx));
                        gameManager.addToGameSummary(winner.getNicknameToken() + " wins!");
                        gameManager.endGame();
                    }
                    return;
                }
            }

            // Advance to next player
            currentPlayerTurn = 1 - currentPlayerTurn;

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

    /**
     * Animate one explosion wave occupying the full frame (t=0..1).
     *
     * Budget: 1 commitCellVisual (= 5 commits) per cell, two time-points total.
     *   0.40  exploding cells → empty
     *   0.70  neighbors → new orb count (may show 2/3/4 = "will explode next wave")
     *
     * The "about to explode" state is already visible at t=0 because the previous
     * frame committed it at t=1.0, so no extra commit is needed here.
     */
    private void animateWaveFrame(WaveFrame wf) {
        final double T_EXPLODE = 0.40;
        final double T_ARRIVE  = 0.70;

        // Collect unique neighbor cells
        Set<Integer> neighborKeys = new HashSet<>();
        for (int[] exp : wf.wave)
            for (int[] d : DIRS) {
                int nr = exp[0] + d[0], nc = exp[1] + d[1];
                if (board.isValid(nr, nc))
                    neighborKeys.add(nr * Board.SIZE + nc);
            }

        // t=T_EXPLODE: exploding cells go empty
        for (int[] exp : wf.wave)
            commitCellVisual(exp[0], exp[1], 0, 0, T_EXPLODE);

        // Compute after-state for neighbors
        int[][] afterOrbs  = copyGrid(wf.vOrbs);
        int[][] afterOwner = copyGrid(wf.vOwner);
        for (int[] exp : wf.wave) {
            afterOrbs[exp[0]][exp[1]]  = 0;
            afterOwner[exp[0]][exp[1]] = 0;
        }
        for (int[] exp : wf.wave)
            for (int[] d : DIRS) {
                int nr = exp[0] + d[0], nc = exp[1] + d[1];
                if (board.isValid(nr, nc)) {
                    afterOrbs[nr][nc]++;
                    afterOwner[nr][nc] = wf.playerIdx;
                }
            }

        // t=T_ARRIVE: neighbors show new orb count (1 commit per cell)
        // If a neighbor reaches critical mass here it will display 2/3/4 dots —
        // this is the "about to explode" state the next wave frame holds at t=0.
        for (int key : neighborKeys) {
            int nr = key / Board.SIZE, nc = key % Board.SIZE;
            commitCellVisual(nr, nc, afterOrbs[nr][nc], afterOwner[nr][nc], T_ARRIVE);
        }

        // Last wave: sync any cell whose final board state differs from afterOrbs
        if (pendingFrames.isEmpty()) {
            for (int r = 0; r < Board.SIZE; r++)
                for (int c = 0; c < Board.SIZE; c++) {
                    Cell cell = board.getCell(r, c);
                    if (cell.orbs != afterOrbs[r][c] || cell.owner != afterOwner[r][c])
                        syncCellAt(r, c, 1.0);
                }
        }

        // Score = cells controlled in the intermediate after-state of this wave
        int cells1 = 0, cells2 = 0;
        for (int r = 0; r < Board.SIZE; r++)
            for (int c = 0; c < Board.SIZE; c++) {
                if (afterOwner[r][c] == 1) cells1++;
                else if (afterOwner[r][c] == 2) cells2++;
            }
        updateScores(cells1, cells2);
    }

    // ── Input to bots ────────────────────────────────────────────────────────

    private void sendGameState(Player player, int playerIdx) {
        int pIdx = playerIdx - 1;
        if (!colorSent[pIdx]) {
            player.sendInputLine(playerIdx == 1 ? "r" : "b");
            colorSent[pIdx] = true;
        }

        int[] opponentMove = lastMove[1 - pIdx];
        if (opponentMove == null) {
            player.sendInputLine("null");
        } else {
            player.sendInputLine(opponentMove[0] + " " + opponentMove[1]);
        }

        for (int r = 0; r < Board.SIZE; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < Board.SIZE; c++) {
                Cell cell = board.getCell(r, c);
                if (cell.owner == 0)      sb.append("..");
                else if (cell.owner == 1) sb.append('r').append(cell.orbs);
                else                      sb.append('b').append(cell.orbs);
            }
            player.sendInputLine(sb.toString());
        }
    }

    // ── End screen ───────────────────────────────────────────────────────────

    @Override
    public void onEnd() {
        List<Player> players = gameManager.getPlayers();

        int cells0 = board.countCells(1);
        int cells1 = board.countCells(2);

        // Ensure scores reflect final cell counts (in case of timeout/invalid action
        // the score may have been set to -1; preserve that to signal disqualification)
        if (players.get(0).getScore() >= 0) players.get(0).setScore(cells0);
        if (players.get(1).getScore() >= 0) players.get(1).setScore(cells1);

        int[] scores = { players.get(0).getScore(), players.get(1).getScore() };
        String[] text = new String[2];

        if (scores[0] < 0 || scores[1] < 0) {
            // One player was disqualified
            text[0] = scores[0] < 0 ? "Disqualified" : "Won " + cells0 + " cells";
            text[1] = scores[1] < 0 ? "Disqualified" : "Won " + cells1 + " cells";
        } else if (cells0 > cells1) {
            text[0] = "Won — " + cells0 + " cells";
            text[1] = "Lost — " + cells1 + " cells";
            gameManager.addTooltip(players.get(0),
                players.get(0).getNicknameToken() + " wins!");
        } else if (cells1 > cells0) {
            text[0] = "Lost — " + cells0 + " cells";
            text[1] = "Won — " + cells1 + " cells";
            gameManager.addTooltip(players.get(1),
                players.get(1).getNicknameToken() + " wins!");
        } else {
            text[0] = "Draw";
            text[1] = "Draw";
        }

        endScreenModule.setScores(scores, text);
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /** Pick a random valid cell for playerIdx. */
    private Action randomAction(Player player, int playerIdx) {
        List<int[]> valid = new ArrayList<>();
        for (int r = 0; r < Board.SIZE; r++)
            for (int c = 0; c < Board.SIZE; c++) {
                int owner = board.getCell(r, c).owner;
                if (owner == playerIdx)
                    valid.add(new int[]{r, c});
            }
        int[] pick = valid.get(random.nextInt(valid.size()));
        return new Action(player, pick[0], pick[1]);
    }

    private static int[][] copyGrid(int[][] src) {
        int[][] dst = new int[Board.SIZE][Board.SIZE];
        for (int r = 0; r < Board.SIZE; r++)
            dst[r] = src[r].clone();
        return dst;
    }
}
