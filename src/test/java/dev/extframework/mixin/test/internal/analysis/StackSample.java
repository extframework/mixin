package dev.extframework.mixin.test.internal.analysis;

public class StackSample {

    // Instance fields
    private int instanceInt;
    private long instanceLong;

    // Static field
    private static double staticDouble;

    public static void main(String[] args) {
        // Create an instance
        StackSample t = new StackSample();

        // Putfield, getfield
        t.instanceInt = 42;
        System.out.println("instanceInt = " + t.instanceInt);

        // Another field
        t.instanceLong = 123456789L;

        // Putstatic, getstatic
        staticDouble = 3.14;
        System.out.println("staticDouble = " + staticDouble);

        // Invoke static
        foo(10, t.instanceLong, 2.2f, staticDouble);

        // Invoke instance
        double d = t.bar(1.23, t.instanceInt);
        System.out.println("bar result = " + d);

        // Make a 2D int array (multi-anew-array) and store/load values
        int[][] arr = new int[2][];
        arr[0] = new int[3];
        arr[1] = new int[4];

        arr[0][1] = (int) (staticDouble * 2);
        System.out.println("arr[0][1] = " + arr[0][1]);

        // A simple lambda uses invokedynamic under the hood
        Runnable r = () -> System.out.println("Lambda invoked!");
        r.run();
    }

    // Static method
    private static void foo(int i, long l, float f, double d) {
        // Simple arithmetic + println
        System.out.println("foo: i + l + f + d = " + (i + l + f + d));
    }

    // Instance method
    private double bar(double dd, int i) {
        // Access instanceLong => getfield
        return dd + i + instanceLong;
    }
}
