import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static ArrayList<String> readFile(File file) throws FileNotFoundException {
        Scanner sc = new Scanner(file);
        ArrayList<String> res = new ArrayList<>();
        while (sc.hasNext()){
            String word = sc.next();
            res.add(word);
        }

        return res;
    }

    public static void main(String[] args) {
        try {
            InputStream in = new BufferedInputStream(new FileInputStream("src/main/resources/test.py"));
            File keywordsFile = new File("src/main/resources/keywords.txt");
            ArrayList<String> keywords = readFile(keywordsFile);
            Lexer lexer = new Lexer(in, keywords);
            List<Token> tokens = lexer.analyze();
            int currentLine = -1;
            for (Token token : tokens) {
                if (token.getLine() != currentLine) {
                    System.out.println();
                    currentLine = token.getLine();
                    System.out.print(currentLine+1 + ":     ");
                }
                System.out.print("(" + token.getType().toString());
                if (!token.getValue().equals("") || token.getType() == TokenType.STRING_LITERAL) {
                    System.out.print(", " + token.getValue());
                }
                System.out.print(")  ");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
