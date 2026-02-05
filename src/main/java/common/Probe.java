package common;

public final class Probe {
    public static volatile String RECEIVER_STAGE = "init";
    public static volatile long RECEIVER_T0 = 0;
    public static void stage(String s) {
        RECEIVER_STAGE = s;
        RECEIVER_T0 = System.nanoTime();
    }
}