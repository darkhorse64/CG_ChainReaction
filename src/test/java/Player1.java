import java.util.Scanner;

/**
 * Greedy bot — plays on own cell with the highest orb count.
 * Reads its assigned color at init and uses it to identify own cells in the grid.
 */
public class Player1 {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String myColor = in.nextLine().trim(); // "r" or "b"

        while (true) {
            in.nextLine(); // redOrbs blueOrbs

            String[][] grid = new String[6][6];
            for (int r = 0; r < 6; r++) {
                String[] tokens = in.nextLine().trim().split(" ");
                for (int c = 0; c < 6; c++) grid[r][c] = tokens[c];
            }

            int bestR = -1, bestC = -1, bestScore = -1;
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 6; c++) {
                    String t = grid[r][c];
                    if (t.startsWith(myColor)) {
                        int score = Integer.parseInt(t.substring(1));
                        if (score > bestScore) {
                            bestScore = score;
                            bestR = r; bestC = c;
                        }
                    }
                }
            }
            System.out.println(bestR + " " + bestC);
        }
    }
}
