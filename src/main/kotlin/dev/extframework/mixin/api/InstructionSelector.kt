package dev.extframework.mixin.api

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

public interface InstructionSelector {
    public fun select(
        instructions: InsnList,
    ): List<AbstractInsnNode>
}