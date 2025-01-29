package dev.extframework.mixin.test

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.MixinExceptionCause
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.Captured
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.InjectionBoundary.*
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.api.Select
import dev.extframework.mixin.api.Stack
import kotlin.test.Test

class TestMixinEngine {
    private fun expectEx(
        cause: MixinExceptionCause,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (ex: InvalidMixinException) {
            if (ex.rootCause != cause) {
                throw ex
            } else {
                ex.printStackTrace()
            }
            return
        }

        throw Exception("Test should have thrown a '$cause'")
    }

    companion object {
        @JvmStatic
        fun <T> receive(value: T, cls: Class<*>) {
            println("recieve")
        }
    }

    @Test
    fun `Test mixin method match`() {
        @Mixin(Destination::class)
        class Test {
            @InjectCode(
                at = Select(HEAD)
            )
            fun methodA() {

            }

            @InjectCode(
                at = Select(HEAD)
            )
            fun overloads(
                asdf: Captured<String>,
            ) {
            }


            @InjectCode(
                "overloads(Ljava/lang/String;)V",
                at = Select(HEAD)
            )
            fun overloads2() {

            }

            @InjectCode(
                "methodA",
                at = Select(HEAD)
            )
            fun methodA2() {

            }

            @InjectCode(
                "overloads (String)",
                at = Select(HEAD)
            )
            fun methodA3() {

            }
        }

//        Sample.callback()

        val engine = MixinEngine(
            RedefinitionFlags.NONE
        )

        engine.registerMixin(
            classNode(Test::class),
        )

        engine.transform(
            Destination::class.java.name,
            classNode(Destination::class),
        )
    }


    @Test()
    fun `Test mixin overload throws`() {
        @Mixin(Destination::class)
        class Test {
            @InjectCode(
                at = Select(HEAD)
            )
            fun overloads() {

            }
        }

        val engine = MixinEngine(
            RedefinitionFlags.NONE
        )

        engine.registerMixin(
            classNode(Test::class),
        )

        expectEx(
            MixinExceptionCause.CodeMethodOverloadAmbiguity
        ) {
            engine.transform(
                Destination::class.java.name,
                classNode(Destination::class),
            )
        }
    }

    class Destination {
        fun methodA() {

        }

        fun overloads() {

        }

        fun overloads(asdf: String) {

        }
    }
}