package dev.extframework.mixin.internal.util

import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.internal.analysis.JvmValueRef
import dev.extframework.mixin.internal.analysis.ObjectValueRef
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

// Assumes that the value you want to manual-box is in the local variables.
internal fun manualBox(
    to: TypeSort,
    localIndex: Int,
    insn: InsnList
) {
    if (to == TypeSort.OBJECT) {
        insn.add(VarInsnNode(Opcodes.ILOAD + to.offset, localIndex))
        return
    }

    val wrapper = when (to) {
        TypeSort.INT -> java.lang.Integer::class
        TypeSort.LONG -> java.lang.Long::class
        TypeSort.FLOAT -> java.lang.Float::class
        TypeSort.DOUBLE -> java.lang.Double::class
        TypeSort.OBJECT -> nothing()
    }

    val constructor = when (to) {
        TypeSort.INT -> "(I)V"
        TypeSort.LONG -> "(J)V"
        TypeSort.FLOAT -> "(F)V"
        TypeSort.DOUBLE -> "(D)V"
        TypeSort.OBJECT -> nothing()
    }

    insn.add(TypeInsnNode(Opcodes.NEW, wrapper.internalName))
    insn.add(InsnNode(Opcodes.DUP))
    insn.add(VarInsnNode(Opcodes.ILOAD + to.offset, localIndex))
    insn.add(MethodInsnNode(Opcodes.INVOKESPECIAL, wrapper.internalName, "<init>", constructor))
}

// Assumes that the value you want to manual-unbox is on the stack
internal fun manuelUnbox(
    ref: JvmValueRef,
    insn: InsnList
) {
    val sort = ref.sort
    if (ref is ObjectValueRef) {
        insn.add(TypeInsnNode(Opcodes.CHECKCAST, ref.objectType.internalName))
        return
    }

    val wrapper = when (sort) {
        TypeSort.INT -> java.lang.Integer::class
        TypeSort.LONG -> java.lang.Long::class
        TypeSort.FLOAT -> java.lang.Float::class
        TypeSort.DOUBLE -> java.lang.Double::class
        TypeSort.OBJECT -> nothing()
    }

    val primitiveValueName = when (sort) {
        TypeSort.INT -> "intValue"
        TypeSort.LONG -> "longValue"
        TypeSort.FLOAT -> "floatValue"
        TypeSort.DOUBLE -> "doubleValue"
        TypeSort.OBJECT -> nothing()
    }

    val primitiveValueDescriptor = when (sort) {
        TypeSort.INT -> "()I"
        TypeSort.LONG -> "()J"
        TypeSort.FLOAT -> "(F)F"
        TypeSort.DOUBLE -> "(D)D"
        TypeSort.OBJECT -> nothing()
    }

    insn.add(TypeInsnNode(Opcodes.CHECKCAST, wrapper.internalName))
    insn.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, wrapper.internalName, primitiveValueName, primitiveValueDescriptor))
}

private fun nothing() : Nothing = throw Exception()