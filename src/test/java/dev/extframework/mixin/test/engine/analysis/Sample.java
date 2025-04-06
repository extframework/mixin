package dev.extframework.mixin.test.engine.analysis;

import dev.extframework.mixin.api.Captured;
import dev.extframework.mixin.api.MixinFlow;
import dev.extframework.mixin.api.Stack;

public class Sample {

    // Dont change this method, tests rely on it
    public int sampleMethod(int i) {
        if (i == 0) {
            System.out.println("This is sick right here");
            System.out.println("Another println for good measure");
        } else {
            System.out.println("Something else");
            System.out.println("Another println for good measure");
            System.out.println("Another println for good measure");
            System.out.println("Another println for good measure");

        }

        if (i == 4) {
            System.out.println("Idk what this is about");
        }

        switch ("i") {
            case "1":
                break;
            case "2":
                break;
            case "3":
                break;
            default:
        }

        return 4;
    }

    public MixinFlow.Result<?> test(
            Stack stack,
            Captured<Integer> integer,
            MixinFlow flow
    ) {
        stack.set(1, 10);
        return flow.on();
    }
}