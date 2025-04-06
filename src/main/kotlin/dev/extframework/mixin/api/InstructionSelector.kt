package dev.extframework.mixin.api

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode

public interface InstructionSelector {
    public fun select(
        method: MethodNode,
        cls: ClassNode
    ): List<AbstractInsnNode>
}