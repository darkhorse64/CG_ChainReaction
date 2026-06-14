package com.codingame.game;

import com.codingame.gameengine.core.AbstractMultiplayerPlayer;
import com.codingame.gameengine.module.entities.Group;

public class Player extends AbstractMultiplayerPlayer {
    public Group hud;

    @Override
    public int getExpectedOutputLines() {
        return 1;
    }

    public Action getAction() throws TimeoutException, InvalidAction {
        String raw = getOutputs().get(0).trim().split("\\s+")[0];
        if (raw.length() < 2)
            throw new InvalidAction("Invalid move notation: " + raw);
        int col = raw.charAt(0) - 'a';
        int row;
        try { row = Board.SIZE - Integer.parseInt(raw.substring(1)); }
        catch (NumberFormatException e) { throw new InvalidAction("Invalid move notation: " + raw); }
        return new Action(this, row, col);
    }
}
