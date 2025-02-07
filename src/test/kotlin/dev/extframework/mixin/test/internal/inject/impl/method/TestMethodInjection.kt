package dev.extframework.mixin.test.internal.inject.impl.method

import dev.extframework.archives.extension.Method
import dev.extframework.archives.transform.ByteCodeUtils
import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.InjectMethod
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.internal.analysis.toValueRef
import dev.extframework.mixin.internal.inject.impl.method.MethodInjectionData
import dev.extframework.mixin.internal.inject.impl.method.MethodInjector
import dev.extframework.mixin.test.classNode
import dev.extframework.mixin.test.load
import dev.extframework.mixin.test.methodFor
import dev.extframework.mixin.test.stripKotlinMetadata
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import java.util.*
import kotlin.test.Test

class TestMethodInjection {
    class MethodDest {
        fun methodOne() {
            println("Doing something in this method")
            methodTwo(1)
        }

        fun methodTwo(arg1: Int) {
            println("arg1 " + arg1)
        }

        fun methodThree(arg1: String, arg2: Int): String {
            return "'$arg1' _ $arg2 " + UUID.randomUUID().toString()
        }
    }

    @Test
    fun `Test build uber method`() {
        val injector = MethodInjector(RedefinitionFlags.ONLY_INSTRUCTIONS)

        val method = injector.buildUberMethod(
            "aMethod",
            false,
            listOf(
                methodFor(MethodDest::class, "methodOne"),
                methodFor(MethodDest::class, "methodTwo"),
                methodFor(MethodDest::class, "methodThree"),
            ).mapIndexed() { i, it ->
                MethodInjectionData(it, i)
            }
        )

        ByteCodeUtils.textifyInsn(method.instructions).forEach { t ->
            println(t)
        }

        val node = classNode(MethodDest::class)
        node.methods.add(method)

        var cls = load(node)
        var loadedMethod = cls.methods.first { it.name == "aMethod" }
        println(
            loadedMethod
                .invoke(cls.getConstructor().newInstance(), 0, arrayOf<Any>())
        )
        println(
            loadedMethod
                .invoke(cls.getConstructor().newInstance(), 2, arrayOf<Any>("Hey testing", 1))
        )
        loadedMethod
            .invoke(cls.getConstructor().newInstance(), 1, arrayOf<Any>(6))
    }

    @Test
    fun `Test remapping`() {
        val instructions = InsnList()
        val injector = MethodInjector(RedefinitionFlags.ONLY_INSTRUCTIONS)

        val method = methodFor(MethodDest::class, "methodOne")

        val injection =
            injector.buildMethodCall(method.maxLocals, 0, Method("aMethod(IJZLjava/lang/String;)Ljava/lang/String;"))
        instructions.add(injection)

        val unwrappingInjection = injector.buildMethodUnwrap(Type.getType("Ljava/lang/String;").toValueRef())
        instructions.add(unwrappingInjection)

        println("")
    }

    @Test
    fun `Test total remapping`() {
        @Mixin(MethodDest::class)
        class MixinClass {
            @InjectMethod
            fun methodTwo(arg1: Int) {
                println("Doing this instead? $arg1")
            }
        }

        val engine = MixinEngine(RedefinitionFlags.ONLY_INSTRUCTIONS)

        engine.registerMixin(classNode(MixinClass::class))

        val transformed = engine.transform(MethodDest::class.java.name, classNode(MethodDest::class))
        stripKotlinMetadata(transformed)

        var cls = load(transformed)
        var loadedMethod = cls.methods.first { it.name == MethodDest::methodOne.name }
        println(
            loadedMethod
                .invoke(cls.getConstructor().newInstance())
        )
    }
}