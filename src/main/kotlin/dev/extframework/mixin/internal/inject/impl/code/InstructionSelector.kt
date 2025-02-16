package dev.extframework.mixin.internal.inject.impl.code

import dev.extframework.mixin.api.FieldAccessType
import dev.extframework.mixin.api.InjectionBoundary
import dev.extframework.mixin.api.InstructionSelector
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

public data class BoundarySelector(
    public val boundary: InjectionBoundary,
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
) : InstructionSelector {
    override fun select(instructions: InsnList): List<AbstractInsnNode> {
        return instructions
            .filter { if (opcode != null) it.opcode == opcode else true }
            .filter { (Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC).contains(it.opcode) }
            .filter { node ->
                when (node) {
                    is MethodInsnNode -> {
                        node.owner == owner.internalName &&
                                node.name == method.name &&
                                node.desc.substringBefore(")") == method.descriptor.substringBefore(")")
                    }

                    is InvokeDynamicInsnNode -> {
                        node.bsm.owner == owner.internalName &&
                                node.name == method.name &&
                                node.desc.substringBefore(")") == method.descriptor.substringBefore(")")
                    }

                    else -> throw Exception("Invalid bytecode?")
                }
            }
    }

    override fun toString(): String {
        return "Invocation @ ${owner.className}:${method}"
    }
}

public class FieldAccessSelector(
    public val owner: Type,
    public val name: String,
    public val access: FieldAccessType,
) : InstructionSelector {
    override fun select(instructions: InsnList): List<AbstractInsnNode> {
        return instructions
            .filter {
                access.opcodes.contains(it.opcode)
            }
            .filterIsInstance<FieldInsnNode>()
            .filter { node ->
                node.owner == owner.internalName && node.name == name
            }
    }

    override fun toString(): String {
        return "Field access @ ${owner.className}:${name}"
    }
}

public class OpcodeSelector(
    public val opcode: Int,
    public val ldc: String?,
) : InstructionSelector {
    override fun select(instructions: InsnList): List<AbstractInsnNode> {
        return instructions.filter { it.opcode == opcode }
            .filter {
                if (ldc != null) {
                    it is LdcInsnNode && it.cst.toString() == ldc
                } else true
            }
    }
}