package com.codingame.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class Board {
    public static final int SIZE = 6;

    /** Critical mass = number of orthogonal neighbours (2 corners, 3 edges, 4 interior). */
    public static int criticalMass(int row, int col) {
        int n = 0;
        if (row > 0)        n++;
        if (row < SIZE - 1) n++;
        if (col > 0)        n++;
        if (col < SIZE - 1) n++;
        return n;
    }

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private Cell[][] grid;

    public Board() {
        grid = new Cell[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                grid[r][c] = new Cell();
    }

    public Cell getCell(int row, int col) {
        return grid[row][col];
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    /**
     * Returns chain-reaction explosions grouped by BFS wave.
     * Each inner list contains the cells [row,col] that explode simultaneously in that wave.
     * Wave 0 = first explosions triggered by the placement, wave 1 = secondary, etc.
     */
    public List<List<int[]>> play(int row, int col, int playerIdx) throws InvalidAction {
        if (!isValid(row, col))
            throw new InvalidAction("Out of bounds: " + row + " " + col);
        Cell cell = grid[row][col];
        if (cell.owner != 0 && cell.owner != playerIdx)
            throw new InvalidAction("Cell (" + row + "," + col + ") does not belong to you");

        cell.owner = playerIdx;
        cell.orbs++;

        List<List<int[]>> waves = new ArrayList<>();

        if (cell.orbs >= criticalMass(row, col)) {
            boolean[][] inQueue = new boolean[SIZE][SIZE];
            Queue<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{row, col});
            inQueue[row][col] = true;

            int totalExplosions = 0;
            final int MAX_EXPLOSIONS = SIZE * SIZE * 50;

            while (!queue.isEmpty() && totalExplosions < MAX_EXPLOSIONS) {
                // Process exactly one BFS level (= one wave of simultaneous explosions)
                int waveSize = queue.size();
                List<int[]> wave = new ArrayList<>();

                for (int w = 0; w < waveSize; w++) {
                    int[] pos = queue.poll();
                    int r = pos[0], c = pos[1];
                    inQueue[r][c] = false;

                    if (grid[r][c].orbs < criticalMass(r, c)) continue;

                    wave.add(new int[]{r, c});
                    totalExplosions++;
                    grid[r][c].orbs  = 0;
                    grid[r][c].owner = 0;

                    for (int[] d : DIRS) {
                        int nr = r + d[0], nc = c + d[1];
                        if (isValid(nr, nc)) {
                            grid[nr][nc].owner = playerIdx;
                            grid[nr][nc].orbs++;
                            if (grid[nr][nc].orbs >= criticalMass(nr, nc) && !inQueue[nr][nc]) {
                                queue.add(new int[]{nr, nc});
                                inQueue[nr][nc] = true;
                            }
                        }
                    }
                }

                if (!wave.isEmpty()) waves.add(wave);

                // Stop chain if opponent has no orbs left — board is conquered
                boolean opponentAlive = false;
                int opponent = (playerIdx == 1) ? 2 : 1;
                outer:
                for (int r = 0; r < SIZE; r++)
                    for (int c = 0; c < SIZE; c++)
                        if (grid[r][c].owner == opponent) { opponentAlive = true; break outer; }
                if (!opponentAlive) break;
            }
        }

        return waves;
    }

    public int countCells(int playerIdx) {
        int total = 0;
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c].owner == playerIdx)
                    total++;
        return total;
    }

    public int countOrbs(int playerIdx) {
        int total = 0;
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c].owner == playerIdx)
                    total += grid[r][c].orbs;
        return total;
    }

    public int totalOrbs() {
        int total = 0;
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                total += grid[r][c].orbs;
        return total;
    }
}
