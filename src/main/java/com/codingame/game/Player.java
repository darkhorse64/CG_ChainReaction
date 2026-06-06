package com.codingame.game;

import com.codingame.gameengine.core.AbstractMultiplayerPlayer;
import com.codingame.gameengine.module.entities.Group;

public class Player extends AbstractMultiplayerPlayer {
    public Group hud;

    @Override
    public int getExpectedOutputLines() {
        return 1;
    }

    public Action getAction() throws TimeoutException, NumberFormatException {
        String[] parts = getOutputs().get(0).trim().split("\\s+");
        return new Action(this, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
