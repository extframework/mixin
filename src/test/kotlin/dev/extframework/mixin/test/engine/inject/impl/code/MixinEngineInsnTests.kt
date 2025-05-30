package dev.extframework.mixin.test.engine.inject.impl.code

import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.*
import dev.extframework.mixin.api.InjectionBoundary.HEAD
import dev.extframework.mixin.api.InjectionType.AFTER
import dev.extframework.mixin.test.classNode
import dev.extframework.mixin.test.load
import dev.extframework.mixin.test.reload
import dev.extframework.mixin.test.stripKotlinMetadata
import net.bytebuddy.agent.ByteBuddyAgent
import org.objectweb.asm.Opcodes
import java.io.PrintStream
import kotlin.test.Test

object StaticDest {
    @JvmStatic
    fun staticTest(param1: Int) {
        println("Something happening" + param1)
    }
}

class Dest {
    fun returnInt(): Int {
        val a = 10
        return a
    }

    fun sample(): String {
        // Local variables of different types
        var a = thisCall()
        println(a)
        val b = 2L
        println(b)
        var c = 3.0
        var d: String? = "Initial"
        println(d)
        val arr = intArrayOf(1, 2, 3)

        println("<here>")
        println("B is: $b")

        // Arithmetic operations
        a += 5
        c += b.toDouble() * a

        // Loop with multiple instructions
        for (i in 0..2) {
            a = a shl 1
            when (i) {
                0 -> d = d?.uppercase()
                1 -> a = a xor 0b1010
                else -> c /= 2
            }
        }

        println("<here>")

        // Exception handling
        try {
            check(a < 100) { "Threshold exceeded" }
            arr[a] = arr[1] / 0  // Will throw ArithmeticException if executed
        } catch (e: Exception) {
            d = e.message?.take(5) ?: "ERROR"
        } finally {
            a -= arr.sum()
        }

        println("<here>")

        // Type checks and smart cast
        if (d is String) {
            a += d.length
        }

        // Synchronization block
        val lock = Any()
        synchronized(lock) {
            c *= 1.5
        }

        // Lambda and functional operations
        val nums = listOf(1, 2, 3).map { it * a }

        // Bitwise operations
        val mask = 0xFF
        val flags = (a and mask).toByte()

        // String manipulation
        return buildString {
            append("A: $a, ")
            append("B: $b, ")
            append("C: ${"%.2f".format(c)}, ")
            append("D: ${d!!}, ")
            append("Nums: ${nums.joinToString()}, ")
            append("Flags: ${flags.toInt().toString(2)}")
        }
    }

    private fun thisCall(): Int {
        return 57
    }
}

class MixinEngineInsnTests {
    @Test
    fun `Test whole injection`() {
        @Mixin(Dest::class)
        class Test {
            @InjectCode(
                point = Select(
                    HEAD
                )
            )
            fun sample(
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                println("First injection into head")
                return flow.on()
            }

            @InjectCode(
                "sample",
                point = Select(
                    HEAD
                )
            )
            fun sampleV2(
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                println("Second injection into head")
                return flow.on()
            }

            @InjectCode(
                "sample",
                point = Select(
                    opcode = Opcode(
                        ldc = LDC("Initial")
                    )
                ),
                type = AFTER
            )
            fun updateLDC(
                stack: Stack,
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                stack.replaceLast("Another value")
                return flow.on()
            }

            @InjectCode(
                "sample",
                point = Select(
                    invoke = Invoke(
                        Dest::class,
                        "thisCall()"
                    )
                ),
                type = AFTER
            )
            fun updateMethodCall(
                stack: Stack,
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                stack.replaceLast(101)
                return flow.on()
            }

//            @InjectCode
//            fun sample() {
//                println("Super basic mixin")
//            }

            @InjectCode(
                "sample",
                point = Select(
                    opcode = Opcode(
                        ldc = LDC("<here>")
                    )
                ),
                ordinal = 2,
                count = 1,
                type = AFTER
            )
            fun updateEndingLDC(
                stack: Stack,
            ) {
                stack.replaceLast("not here")
            }

            @InjectCode(
                "sample",
                point = Select(
                    invoke = Invoke(
                        clsName = "kotlin/collections/ArraysKt",
                        method = "sum([I)I"
                    )
                ),
                ordinal = 1,
                type = AFTER
            )
            fun changeSumReturn(
                stack: Stack,
            ) {
                println("Summing first")
                stack.replaceLast(15)
            }

            @InjectCode(
                "sample",
                point = Select(
                    invoke = Invoke(
                        PrintStream::class,
                        method = "println(Ljava/lang/Object;)V"
                    )
                ),
                type = AFTER,
                locals = [2]
            )
            fun captureLocalVariable(
                captured: Captured<Long>
            ) {
                captured.set(12)
            }
        }

        val engine = MixinEngine(
            RedefinitionFlags.FULL,
        )

        engine.registerMixin(classNode(Test::class))

        val transformed = engine.transform(classNode(Dest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        val obj = cls.getConstructor().newInstance()
        println(cls.getMethod(Dest::sample.name).invoke(obj))
    }

    @Test
    fun `Test instruction remapping with source injection`() {
        @Mixin(Dest::class)
        class OnlyInsnTest {
            @InjectCode(
                point = Select(
                    HEAD
                )
            )
            fun sample(
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                println("An injection into the head here?")
                return flow.on()
            }

            @InjectCode(
                "sample",
                point = Select(
                    InjectionBoundary.TAIL
                )
            )
            fun sampleTail(
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                println("Here is the tail :)")
                return flow.on()
            }

            @InjectCode(
                "sample",
                point = Select(
                    opcode = Opcode(
                        ldc = LDC("<here>")
                    )
                ),
                type = AFTER
            )
            fun sampleLDC(
                stack: Stack,
            ) {
                stack.replaceLast("Not <here>")
                println("Here is the tail :)")
            }
        }

        val engine = MixinEngine(
            RedefinitionFlags.ONLY_INSTRUCTIONS,
        )

        engine.registerMixin(classNode(OnlyInsnTest::class))

        val transformed = engine.transform(classNode(Dest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        val obj = cls.getConstructor().newInstance()
        println(cls.getMethod(Dest::sample.name).invoke(obj))
    }

    @Test
    fun `Test reloading mixins`() {
        @Mixin(Dest::class)
        class FirstMixin {
            @InjectCode(
                point = Select(
                    opcode = Opcode(
                        Opcodes.ISTORE
                    )
                ),
                type = AFTER,
                locals = [1]
            )
            fun returnInt(
                captured: Captured<Int>
            ) {
                println("A First happened here")
                captured.set(11)
            }
        }

        @Mixin(Dest::class)
        class SecondMixin {
            @InjectCode
            fun returnInt(
                flow: MixinFlow
            ): MixinFlow.Result<*> {
                println("A second mixin happened here")
                return flow.yield(12)
            }
        }

        val agent = ByteBuddyAgent.install()

        val engine = MixinEngine(
            RedefinitionFlags.ONLY_INSTRUCTIONS,
        )

        // First mixin
        engine.registerMixin(classNode(FirstMixin::class))

        val transformed = engine.transform(classNode(Dest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        val obj = cls.getConstructor().newInstance()
        check(cls.getMethod(Dest::returnInt.name).invoke(obj) == 11)

        println("=============================================")

        // Second
        engine.registerMixin(classNode(SecondMixin::class))

        val transformed2 = engine.transform(classNode(Dest::class))
        stripKotlinMetadata(transformed2)

        agent.reload(cls, transformed2)

        check(cls.getMethod(Dest::returnInt.name).invoke(obj) == 12)
    }

    @Mixin(StaticDest::class)
    object StaticMixin {
        @InjectCode(
            locals = [0]
        )
        @JvmStatic
        fun staticTest(
            captured: Captured<Int>
        ) {
            println("A First happened here: " + captured.get())
        }

    }

    @Test
    fun `Test static mixins`() {
        val engine = MixinEngine(
            RedefinitionFlags.ONLY_INSTRUCTIONS,
        )

        // First mixin
        engine.registerMixin(classNode(StaticMixin::class))

        val transformed = engine.transform(classNode(StaticDest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        cls.getMethod(StaticDest::staticTest.name, Int::class.java).invoke(null, 10)
    }
}