import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
public class Token {
    private TokenType type;
    private String value;
    private int line;
}
