package dev.extframework.mixin.test.internal.inject.impl.code

import dev.extframework.mixin.api.Captured
import dev.extframework.mixin.internal.inject.impl.code.InstructionInjector
import dev.extframework.mixin.test.methodFor
import jdk.internal.org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import kotlin.test.Test

class TestParameterInfer {
    fun testSignature(
        captured: Captured<String>,
        captured1: Captured<String>,
        list: List<String>,
        captured2: Captured<Double>,
        captured3: Captured<Int>,
    ) {

    }

    @Test
    fun `Test signature visitor`() {
        val method = methodFor(TestParameterInfer::class, ::testSignature.name)

        val reader = SignatureReader(method.signature)

        val visitor = object : SignatureVisitor(Opcodes.ASM5) {
            override fun visitParameterType(): SignatureVisitor? {
                return super.visitParameterType()
            }

            override fun visitClassType(name: String?) {
                super.visitClassType(name)
            }
        }

        reader.accept(visitor)
    }

    @Test
    fun `Test infer`() {
        val method = methodFor(TestParameterInfer::class, ::testSignature.name)
        val params = InstructionInjector.inferLocalsFromSignature(method.signature)

        println(params)
    }
}