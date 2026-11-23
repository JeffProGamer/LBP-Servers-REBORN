import cwlib.resources.RScript;
import cwlib.io.serializer.Serializer;
import java.lang.reflect.Method;

public class CheckRScript2 {
    public static void main(String[] args) {
        for (Method m : RScript.class.getDeclaredMethods()) {
            System.out.println(m);
        }
    }
}