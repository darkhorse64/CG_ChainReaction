import java.util.Scanner;

/**
 * Random bot — picks a cell at random.
 */
public class Player2 {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String myColor = in.nextLine().trim(); // "r" or "b"

        while (true) {
            String lastMove = in.nextLine().trim(); // "row col" or "null"
            int pickR = -1, pickC = -1;
            for (int r = 0; r < 6; r++) {
                String line = in.nextLine().trim();
            }
            System.out.println("random");
        }
    }
}
