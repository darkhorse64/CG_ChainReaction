import com.codingame.gameengine.runner.MultiplayerGameRunner;

public class Main {
    public static void main(String[] args) {
        MultiplayerGameRunner runner = new MultiplayerGameRunner();
        runner.addAgent(Player1.class);
        runner.addAgent(Player2.class);
        // To use an external agent:
        // runner.addAgent("python3 /path/to/player.py");
        runner.start();
    }
}
