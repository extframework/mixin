package dev.extframework.mixin.test.internal.analysis

import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.internal.analysis.ObjectValueRef
import dev.extframework.mixin.internal.analysis.SortValueRef
import dev.extframework.mixin.internal.analysis.SimulatedFrame
import dev.extframework.mixin.internal.analysis.analyzeFrames
import dev.extframework.mixin.test.classNode
import org.objectweb.asm.Type
import kotlin.test.Test

class TestStackCompute {
    @Test
    fun `Test basic test returns correct stack`() {
        val node = classNode(Sample::class)
        val method = node.methods.first {
            it.name == Sample::sampleMethod.name
        }

        val end = method.instructions.get(4)
        val frames = analyzeFrames(
            end,
            SimulatedFrame(
                listOf(), listOf(
                    ObjectValueRef(
                        Type.getType(Sample::class.java),
                    ),
                    SortValueRef(TypeSort.INT)
                )
            )
        )

        check(
            frames.stack == listOf(
                SortValueRef(TypeSort.INT),
                SortValueRef(TypeSort.INT),
            )
        )
    }

    @Test
    fun `Test more complicated example`() {
        val node = classNode(StackSample::class)
        val method = node.methods.first {
            it.name == StackSample::main.name
        }

        val end = method.instructions.get(
//            40
            method.instructions.size() - 1
        )
        val frames = analyzeFrames(
            end,
            SimulatedFrame(
                listOf(), listOf(
                    ObjectValueRef(
                        Type.getType(Array<String>::class.java),
                    ),
                )
            )
        )

        check(frames.stack.isEmpty())
    }
}