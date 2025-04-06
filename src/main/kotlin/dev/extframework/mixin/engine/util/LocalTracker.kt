package dev.extframework.mixin.engine.util

import dev.extframework.mixin.api.TypeSort
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

public class LocalTracker(
    initial: Int
) {
    public var current: Int = initial
        private set

    public fun increment(
        // Unfortunately (but it's a good thing) different types have different sizes in the JMV
        sort: TypeSort
    ): Int {
        val old = current
        current = current + sort.size

        return old
    }

    public companion object {
        public fun calculateFor(
            method: MethodNode,
        ): LocalTracker {
            val isStatic = method.access.and(ACC_STATIC) == ACC_STATIC
            val parameters = method.method().argumentTypes.size

            val largestVar = method.instructions.asSequence().filterIsInstance<VarInsnNode>()
                // VarInsnNode can also hold RET
                .filterNot { it.opcode == Opcodes.RET }.map {
                    it.`var` + sizeOf(it.opcode) - 1
                }.maxOrNull()

            val max = ((if (isStatic) -1 else 0) + parameters).coerceAtLeast(
                largestVar ?: -1
            )

            return LocalTracker((max) + 1)
        }

        private fun sizeOf(
            opcode: Int,
        ) : Int {
           return when (opcode) {
                Opcodes.ILOAD,
                Opcodes.FLOAD,
                Opcodes.ALOAD,

                Opcodes.ISTORE,
                Opcodes.FSTORE,
                Opcodes.ASTORE -> 1

                Opcodes.DLOAD,
                Opcodes.LLOAD,

                Opcodes.DSTORE,
                Opcodes.LSTORE -> 2
               else -> throw Exception("Unknown type")
            }
        }
    }
}