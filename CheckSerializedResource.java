import cwlib.types.SerializedResource;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class CheckSerializedResource {
    public static void main(String[] args) {
        for (Method m : SerializedResource.class.getDeclaredMethods()) {
            System.out.println(m);
        }
        for (Constructor<?> c : SerializedResource.class.getDeclaredConstructors()) {
            System.out.println("Constructor: " + c);
        }
    }
}