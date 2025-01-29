package dev.extframework.mixin.test.internal.inject.impl.code

import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.*
import dev.extframework.mixin.test.classNode
import dev.extframework.mixin.test.load
import java.io.PrintStream
import kotlin.test.Test

class Dest {
    fun sample(): Int {
        println("Hey this is cool")
        return 5
    }

    fun addOne(int: Int): Int {
        return int + 1
    }
}

class MixinEngineInsnTests {
    @Test
    fun `Test whole injection`() {
        @Mixin(Dest::class)
        class Test {
            @InjectCode(
                at = Select(
                    invoke = Invoke(
                        PrintStream::class,
                        "println(Ljava/lang/Object;)V"
                    )
                )
            )
            fun sample(
                stack: Stack,
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                println("This is here laso")
                return flow.on()
            }

            @InjectCode(
                "sample",
                at = Select(
                    InjectionBoundary.TAIL
                )
            )
            fun sampleV2(
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                return flow.yield(8)
            }

            @InjectCode(
                at = Select(
                    InjectionBoundary.HEAD,
                ),
                locals = [1]
            )
            fun addOne(
                captured: Captured<Int>,
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                captured.set(captured.get() + 1)
                return flow.on()
            }
        }

        val engine = MixinEngine(
            RedefinitionFlags.FULL,
        )

        engine.registerMixin(classNode(Test::class))

        val transformed = engine.transform(Dest::class.java.name, classNode(Dest::class))

        val cls = load(transformed)
        val obj = cls.getConstructor().newInstance()
        println(cls.getMethod(Dest::sample.name).invoke(obj))
        println(cls.getMethod(Dest::addOne.name, Int::class.java).invoke(obj, 5))
    }
}