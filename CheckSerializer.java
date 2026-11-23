import cwlib.io.serializer.Serializer;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class CheckSerializer {
    public static void main(String[] args) {
        for (Method m : Serializer.class.getDeclaredMethods()) {
            if (m.getName().contains("deserial") || m.getName().contains("load") || m.getName().contains("parse")) {
                System.out.println(m);
            }
        }
        for (Constructor<?> c : Serializer.class.getDeclaredConstructors()) {
            System.out.println("Constructor: " + c);
        }
    }
}