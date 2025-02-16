package dev.extframework.mixin.test.internal.inject.impl.code

import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.Captured
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.Invoke
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.api.MixinFlow
import dev.extframework.mixin.api.Opcode
import dev.extframework.mixin.api.Opcodes
import dev.extframework.mixin.api.Select
import dev.extframework.mixin.api.Stack
import dev.extframework.mixin.test.classNode
import dev.extframework.mixin.test.load
import dev.extframework.mixin.test.stripKotlinMetadata
import kotlin.test.Test

class TestCapturing {
    class Dest {
        fun singleLocal() : Long {
            val a = 5L

            return a
        }

        fun largeStack() {
            exampleCall("Hey", 10, 20, Any())
        }

        fun exampleCall(
            str: String, int: Int, long: Long, any: Any
        ) {
            println(str)
            check(str == "Way")
            check(int == 11)
            check(long == 21L)
        }
    }

    @Test
    fun `Test capturing single local`() {
        @Mixin(Dest::class)
        class SingleLocalMixin {
            @InjectCode(
                point = Select(
                    opcode = Opcode(
                        Opcodes.LLOAD
                    )
                ),
                locals = [0]
            )
            fun singleLocal(
                captured: Captured<Long>,
                flow: MixinFlow
            ) : MixinFlow.Result<*> {
                println(captured.get())
                captured.set(8)
                return flow.on()// flow.yield(captured.get())
            }
        }

        val engine = MixinEngine(
            RedefinitionFlags.FULL,
        )
        engine.registerMixin(classNode(SingleLocalMixin::class))

        val transformed = engine.transform(
            Dest::class.java.name, classNode(
                Dest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        val obj = cls.getConstructor().newInstance()

        val value = 8L

        check(cls.getMethod(Dest::singleLocal.name).invoke(obj) == value)
    }

    @Test
    fun `Test capturing large stack`() {
        @Mixin(Dest::class)
        class LargeStackMixin {
            @InjectCode(
                "largeStack",
                point = Select(
                    invoke = Invoke(
                        Dest::class,
                        "exampleCall(Ljava/lang/String;IJLjava/lang/Object;)V",
                    )
                ),
            )
            fun injected(
                stack: Stack,
            ) {
                println(stack.iterator().asSequence().toList())
                stack.set(0, "Way")
                stack.set(1, 11)
                stack.set(2, 21L)
            }
        }

        val engine = MixinEngine(
            RedefinitionFlags.FULL,
        )
        engine.registerMixin(classNode(LargeStackMixin::class))

        val transformed = engine.transform(
            Dest::class.java.name, classNode(
                Dest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        val obj = cls.getConstructor().newInstance()

        cls.getMethod(Dest::largeStack.name).invoke(obj)
    }

}