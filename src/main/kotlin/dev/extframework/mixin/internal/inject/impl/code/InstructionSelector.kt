package dev.extframework.mixin.internal.inject.impl.code

import dev.extframework.mixin.api.InjectionBoundary
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

public interface InstructionSelector {
    public val ordinal: Int

    public fun select(
        instructions: InsnList,
    ): List<AbstractInsnNode>
}

public data class BoundarySelector(
    public val boundary: InjectionBoundary,
    override val ordinal: Int,
) : InstructionSelector {
    override fun select(instructions: InsnList): List<AbstractInsnNode> {
        fun isReturn(opcode: Int): Boolean {
            return (Opcodes.IRETURN..Opcodes.RETURN).contains(opcode)
        }

        return when (boundary) {
            InjectionBoundary.HEAD -> {
                listOf(instructions.first())
            }

            InjectionBoundary.TAIL -> {
                listOf(instructions.last {
                    isReturn(it.opcode)
                })
            }

            InjectionBoundary.RETURN -> {
                instructions.filter {
                    isReturn(it.opcode)
                }
            }

            InjectionBoundary.IGNORE -> {
                throw IllegalArgumentException()
            }
        }
    }
}

public class InvocationSelector(
    public val owner: Type,
    public val method: Method,
    public val opcode: Int?,
    override val ordinal: Int,
) : InstructionSelector {
    override fun select(instructions: InsnList): List<AbstractInsnNode> {
        return listOf(
            instructions
                .filter { if (opcode != null) it.opcode == opcode else true }
                .filter { (Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC).contains(it.opcode) }
                .filter { node ->
                    when (node) {
                        is MethodInsnNode -> {
                            node.owner == owner.internalName && Method(node.name, node.desc) == method
                        }

                        is InvokeDynamicInsnNode -> {
                            node.bsm.owner == owner.internalName && Method(node.name, node.desc) == method
                        }

                        else -> throw Exception("Invalid bytecode?")
                    }
                }[ordinal]
        )
    }
}