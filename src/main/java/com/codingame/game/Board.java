package com.codingame.game;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Board {
    public static final int SIZE = 8;

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
     * Returns the ordered list of explosion events for animation.
     * Each event is int[]{row, col} in BFS order.
     */
    public List<int[]> play(int row, int col, int playerIdx) throws InvalidAction {
        if (!isValid(row, col))
            throw new InvalidAction("Out of bounds: " + row + " " + col);
        Cell cell = grid[row][col];
        if (cell.owner != playerIdx)
            throw new InvalidAction("Cell (" + row + "," + col + ") does not belong to you");

        cell.owner = playerIdx;
        cell.orbs++;

        List<int[]> explosions = new ArrayList<>();

        if (cell.orbs >= criticalMass(row, col)) {
            boolean[][] inQueue = new boolean[SIZE][SIZE];
            Queue<int[]> queue = new LinkedList<>();
            queue.add(new int[]{row, col});
            inQueue[row][col] = true;

            final int MAX_EXPLOSIONS = SIZE * SIZE * 50;
            while (!queue.isEmpty() && explosions.size() < MAX_EXPLOSIONS) {
                int[] pos = queue.poll();
                int r = pos[0], c = pos[1];
                inQueue[r][c] = false;

                if (grid[r][c].orbs < criticalMass(r, c)) continue;

                explosions.add(new int[]{r, c});
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
        }

        return explosions;
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
