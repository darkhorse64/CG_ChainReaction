import java.util.Scanner;

/**
 * Naive bot — picks the first own cell found.
 * Reads its assigned color at init and uses it to identify own cells in the grid.
 */
public class Player2 {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String myColor = in.nextLine().trim(); // "r" or "b"

        while (true) {
            String lastMove = in.nextLine().trim(); // "row col" or "null"
            int pickR = -1, pickC = -1;
            for (int r = 0; r < 8; r++) {
                String line = in.nextLine().trim();
                for (int c = 0; c < 8; c++) {
                    if (line.substring(c * 2, c * 2 + 2).startsWith(myColor) && pickR == -1) {
                        pickR = r; pickC = c;
                    }
                }
            }
            System.out.println(pickR + " " + pickC);
        }
    }
}
