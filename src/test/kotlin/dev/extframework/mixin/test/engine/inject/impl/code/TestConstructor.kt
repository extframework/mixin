package dev.extframework.mixin.test.engine.inject.impl.code

import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.*
import dev.extframework.mixin.test.classNode
import dev.extframework.mixin.test.load
import dev.extframework.mixin.test.stripKotlinMetadata
import org.junit.jupiter.api.Test

class TestConstructor {
    data class BasicType(
        val any: Any
    )

    open class SuperType {
        constructor(str: String) {
            println(str)
        }
    }

    class Dest : SuperType {
        constructor() : super("Super type called!") {
            println("Here")
        }

        fun invokingConstructor(): Any {
            return BasicType(Any())
        }
    }

    @Mixin(Dest::class)
    object ConstructorMixin {
        @InjectCode(
            "<init>",
            point = Select(
                InjectionBoundary.TAIL,
            )
        )
        @JvmStatic
        fun basicMixin(
            flow: MixinFlow
        ) : MixinFlow.Result<*> {
            println("Here from injection")
            return flow.yield()
        }

        @InjectCode(
            "<init>"
        )
        fun nonTail() {
            println("Here from HEAD injection in constructor")
        }

        @InjectCode(
            "<init>",
        )
        @JvmStatic
        fun staticHead() {
            println("Here from STATIC HEAD injection in constructor")
        }
    }

    @Test
    fun `Test constructor mixin`() {
        val engine = MixinEngine(
            RedefinitionFlags.ONLY_INSTRUCTIONS,
        )
        engine.registerMixin(classNode(ConstructorMixin::class))

        val transformed = engine.transform(classNode(Dest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        cls.getConstructor().newInstance()
    }

    @kotlin.test.Test
    fun `Test inject at constructor`() {
        @Mixin(Dest::class)
        class ConstructorInvocationMixin {
            @InjectCode(
                "invokingConstructor",
                point = Select(
                    invoke = Invoke(
                        Any::class,
                        "<init>()"
                    )
                ),
                type = InjectionType.AFTER
            )
            fun inject(
                stack: Stack
            ) {
                println(stack)

                stack.replaceLast("Test")
            }
        }

        val engine = MixinEngine(
            RedefinitionFlags.ONLY_INSTRUCTIONS,
        )
        engine.registerMixin(classNode(ConstructorInvocationMixin::class))

        val transformed = engine.transform(classNode(Dest::class))
        stripKotlinMetadata(transformed)

        val cls = load(transformed)
        val obj = cls.getConstructor().newInstance()
        check(cls.getMethod("invokingConstructor").invoke(obj) == BasicType("Test"))
    }
}