//package dev.extframework.mixin.internal.analysis
//
//import dev.extframework.mixin.api.JvmType
//import org.objectweb.asm.Opcodes
//import org.objectweb.asm.Type
//import org.objectweb.asm.tree.AbstractInsnNode
//import org.objectweb.asm.tree.InsnList
//import org.objectweb.asm.tree.VarInsnNode
//
//// Note that this does not fully compute the exact values of locals (for example Ints, Chars, etc. are all treated as one)
//internal fun computeLocalsState(
//    targetInstructions: InsnList,
//    targetPoint: AbstractInsnNode,
//    isStatic: Boolean,
//    parameters: List<Type>
//): ArrayList<JvmType> {
//    val locals = ArrayList<JvmType>()
//    if (!isStatic) {
//        locals.add(JvmType.OBJECT)
//    }
//    parameters.forEach {
//        locals.add(it.toJvmType())
//    }
//
//    for (node in targetInstructions) {
//        if (node == targetPoint) break
//        if (node !is VarInsnNode) continue
//
//        when (node.opcode) {
//            Opcodes.ASTORE -> locals.add(node.`var`, JvmType.OBJECT)
//            Opcodes.ISTORE -> locals.add(node.`var`, JvmType.INT)
//            Opcodes.LSTORE -> locals.add(node.`var`, JvmType.LONG)
//            Opcodes.FSTORE -> locals.add(node.`var`, JvmType.FLOAT)
//            Opcodes.DSTORE -> locals.add(node.`var`, JvmType.DOUBLE)
//        }
//    }
//
//    return locals
//}