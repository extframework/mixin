package dev.extframework.mixin.test.internal.analysis;

import dev.extframework.mixin.api.Captured;
import dev.extframework.mixin.api.MixinFlow;
import dev.extframework.mixin.api.Stack;

public class Sample {
    public int sampleMethod(int i) {
        if (i < 5) {
            if (i % 2 == 0) {
                System.out.println("Hey got here I is " + i);
            }
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