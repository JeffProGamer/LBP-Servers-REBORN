import cwlib.resources.RScript;
import java.lang.reflect.Method;

public class CheckRScript {
    public static void main(String[] args) {
        for (Method m : RScript.class.getDeclaredMethods()) {
            if (m.getName().contains("deserial") || m.getName().contains("load") || m.getName().contains("serialize")) {
                System.out.println(m);
            }
        }
    }
}