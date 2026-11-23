import cwlib.io.serializer.Serializer;
import java.lang.reflect.Method;

public class CheckSerializer2 {
    public static void main(String[] args) {
        for (Method m : Serializer.class.getDeclaredMethods()) {
            System.out.println(m);
        }
    }
}