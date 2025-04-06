package dev.extframework.mixin.test.engine.analysis;

public class JavaControlFlowTryCatch {
    void testIt() {
        try {
            System.out.println("Printing something");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("Printing finally");
        }
    }
}
