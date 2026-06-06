package com.codingame.game;

public class Cell {
    public int owner; // 0=empty, 1=player1, 2=player2
    public int orbs;  // 0..3

    public Cell() {
        this.owner = 0;
        this.orbs = 0;
    }

    public Cell(Cell other) {
        this.owner = other.owner;
        this.orbs = other.orbs;
    }
}
