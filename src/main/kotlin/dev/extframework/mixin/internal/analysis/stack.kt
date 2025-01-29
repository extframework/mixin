//package dev.extframework.mixin.internal.analysis
//
//import dev.extframework.mixin.api.JvmType
//import dev.extframework.mixin.api.JvmType.DOUBLE
//import dev.extframework.mixin.api.JvmType.FLOAT
//import dev.extframework.mixin.api.JvmType.INT
//import dev.extframework.mixin.api.JvmType.LONG
//import dev.extframework.mixin.api.JvmType.OBJECT
//import org.objectweb.asm.Opcodes.*
//import org.objectweb.asm.Type
//import org.objectweb.asm.commons.Method
//import org.objectweb.asm.tree.AbstractInsnNode
//import org.objectweb.asm.tree.FieldInsnNode
//import org.objectweb.asm.tree.InsnList
//import org.objectweb.asm.tree.InvokeDynamicInsnNode
//import org.objectweb.asm.tree.LdcInsnNode
//import org.objectweb.asm.tree.MethodInsnNode
//import org.objectweb.asm.tree.MultiANewArrayInsnNode
//
//// Generously AI generated. I was lucky to not have to write 80%
//// of this myself.
//internal fun computeStackState(
//    targetInstructions: InsnList,
//    targetPoint: AbstractInsnNode
//): List<JvmType> {
//    val stack = ArrayList<JvmType>()
//
//    for (node in targetInstructions) {
//        if (node == targetPoint) break
//
//        println(stack.toString() + " " + targetInstructions.indexOf(node))
//
//        when (node.opcode) {
//
//            // ========== Constants ==========
//            ACONST_NULL -> {
//                stack.add(OBJECT)
//            }
//
//            ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
//            BIPUSH, SIPUSH -> {
//                stack.add(INT)
//            }
//
//            LCONST_0, LCONST_1 -> {
//                stack.add(LONG)
//            }
//
//            FCONST_0, FCONST_1, FCONST_2 -> {
//                stack.add(FLOAT)
//            }
//
//            DCONST_0, DCONST_1 -> {
//                stack.add(DOUBLE)
//            }
//
//            // ========== Load instructions ==========
//            ILOAD -> stack.add(INT)
//            LLOAD -> stack.add(LONG)
//            FLOAD -> stack.add(FLOAT)
//            DLOAD -> stack.add(DOUBLE)
//            ALOAD -> stack.add(OBJECT)
//
//            // ========== Array load ==========
//            IALOAD -> {
//                stack.safePop(INT)    // index
//                stack.safePop(OBJECT) // array ref
//                stack.add(INT)
//            }
//
//            LALOAD -> {
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//                stack.add(LONG)
//            }
//
//            FALOAD -> {
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//                stack.add(FLOAT)
//            }
//
//            DALOAD -> {
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//                stack.add(DOUBLE)
//            }
//
//            AALOAD -> {
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//                stack.add(OBJECT)
//            }
//
//            BALOAD, CALOAD, SALOAD -> {
//                stack.safePop(INT)    // index
//                stack.safePop(OBJECT) // array ref
//                // On the JVM stack, byte/char/short → int
//                stack.add(INT)
//            }
//
//            // ========== Store instructions ==========
//            ISTORE -> {
//                stack.safePop(INT)
//            }
//
//            LSTORE -> {
//                stack.safePop(LONG)
//            }
//
//            FSTORE -> {
//                stack.safePop(FLOAT)
//            }
//
//            DSTORE -> {
//                stack.safePop(DOUBLE)
//            }
//
//            ASTORE -> {
//                stack.safePop(OBJECT)
//            }
//
//            // ========== Array store ==========
//            IASTORE -> {
//                stack.safePop(INT)    // value
//                stack.safePop(INT)    // index
//                stack.safePop(OBJECT) // array ref
//            }
//
//            LASTORE -> {
//                stack.safePop(LONG)
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//            }
//
//            FASTORE -> {
//                stack.safePop(FLOAT)
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//            }
//
//            DASTORE -> {
//                stack.safePop(DOUBLE)
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//            }
//
//            AASTORE -> {
//                stack.safePop(OBJECT) // value
//                stack.safePop(INT)    // index
//                stack.safePop(OBJECT) // array ref
//            }
//
//            BASTORE, CASTORE, SASTORE -> {
//                stack.safePop(INT)
//                stack.safePop(INT)
//                stack.safePop(OBJECT)
//            }
//
//            // ========== Stack management ==========
//            POP -> {
//                stack.safePop()
//            }
//
//            POP2 -> {
//                // If top is category-2 (LONG/DOUBLE), pop just one
//                // else pop two category-1
//                if (stack.isEmpty()) {
//                    throw IllegalStateException("Stack underflow for POP2")
//                }
//                val top = stack.removeAt(stack.lastIndex)
//                if (top != LONG && top != DOUBLE) {
//                    // pop one more
//                    stack.safePop()
//                }
//            }
//
//            DUP -> {
//                if (stack.isEmpty()) {
//                    throw IllegalStateException("Stack underflow for DUP")
//                }
//                val top = stack.last()
//                stack.add(top)
//            }
//
//            DUP_X1 -> {
//                // [top2, top1] -> [top1, top2, top1]
//                if (stack.size < 2) {
//                    throw IllegalStateException("Stack underflow for DUP_X1")
//                }
//                val top1 = stack.removeAt(stack.lastIndex)
//                val top2 = stack.removeAt(stack.lastIndex)
//                // push in order top1, top2, top1
//                stack.add(top1)
//                stack.add(top2)
//                stack.add(top1)
//            }
//
//            DUP_X2 -> {
//                // This depends on whether the second item is category-1 or category-2
//                // Some logic:
//                if (stack.size < 2) {
//                    throw IllegalStateException("Stack underflow for DUP_X2")
//                }
//                val top1 = stack.removeAt(stack.lastIndex)
//                val top2 = stack.removeAt(stack.lastIndex)
//
//                if (top2 == LONG || top2 == DOUBLE) {
//                    // [cat2, cat1] -> [cat1, cat2, cat1]
//                    stack.add(top1)
//                    stack.add(top2)
//                    stack.add(top1)
//                } else {
//                    // We need a third item
//                    if (stack.isEmpty()) {
//                        throw IllegalStateException("Stack underflow for DUP_X2 - need 3rd item")
//                    }
//                    val top3 = stack.removeAt(stack.lastIndex)
//                    // [top3, top2, top1] => [top1, top3, top2, top1]
//                    stack.add(top1)
//                    stack.add(top3)
//                    stack.add(top2)
//                    stack.add(top1)
//                }
//            }
//
//            DUP2 -> {
//                // If top is category-2 => duplicate it
//                // If top is category-1 => need to duplicate top two
//                if (stack.isEmpty()) {
//                    throw IllegalStateException("Stack underflow for DUP2")
//                }
//                val t1 = stack.removeAt(stack.lastIndex)
//                if (t1 == LONG || t1 == DOUBLE) {
//                    // Category-2 -> push t1 twice
//                    stack.add(t1)
//                    stack.add(t1)
//                } else {
//                    // Need second top
//                    if (stack.isEmpty()) {
//                        throw IllegalStateException("Stack underflow for DUP2 (need second item)")
//                    }
//                    val t2 = stack.removeAt(stack.lastIndex)
//                    // push [t2, t1] again
//                    stack.add(t2)
//                    stack.add(t1)
//                    stack.add(t2)
//                    stack.add(t1)
//                }
//            }
//
//            DUP2_X1, DUP2_X2 -> {
//                // Rarely used, more complex duplication (category-2 vs category-1 combos)
//                // Implement similarly to DUP_X1/X2 but for two or cat-2 items
//                TODO("Implement DUP2_X1 / DUP2_X2 if needed")
//            }
//
//            SWAP -> {
//                // Swap top two category-1 values
//                if (stack.size < 2) {
//                    throw IllegalStateException("Stack underflow for SWAP")
//                }
//                val t1 = stack.removeAt(stack.lastIndex)
//                val t2 = stack.removeAt(stack.lastIndex)
//                // Must both be category-1
//                if (t1 == LONG || t1 == DOUBLE ||
//                    t2 == LONG || t2 == DOUBLE
//                ) {
//                    throw IllegalStateException("SWAP requires two category-1 values")
//                }
//                stack.add(t1)
//                stack.add(t2)
//            }
//
//            // ========== Arithmetic ==========
//            IADD, ISUB, IMUL, IDIV, IREM -> {
//                stack.safePop(INT)
//                stack.safePop(INT)
//                stack.add(INT)
//            }
//
//            LADD, LSUB, LMUL, LDIV, LREM -> {
//                stack.safePop(LONG)
//                stack.safePop(LONG)
//                stack.add(LONG)
//            }
//
//            FADD, FSUB, FMUL, FDIV, FREM -> {
//                stack.safePop(FLOAT)
//                stack.safePop(FLOAT)
//                stack.add(FLOAT)
//            }
//
//            DADD, DSUB, DMUL, DDIV, DREM -> {
//                stack.safePop(DOUBLE)
//                stack.safePop(DOUBLE)
//                stack.add(DOUBLE)
//            }
//
//            // ========== Negate / shift / bitwise / iinc, etc. ==========
//            INEG -> {
//                stack.safePop(INT)
//                stack.add(INT)
//            }
//
//            LNEG -> {
//                stack.safePop(LONG)
//                stack.add(LONG)
//            }
//
//            FNEG -> {
//                stack.safePop(FLOAT)
//                stack.add(FLOAT)
//            }
//
//            DNEG -> {
//                stack.safePop(DOUBLE)
//                stack.add(DOUBLE)
//            }
//
//            ISHL, ISHR, IUSHR, IAND, IOR, IXOR -> {
//                // All take 2 ints, push 1 int
//                stack.safePop(INT)
//                stack.safePop(INT)
//                stack.add(INT)
//            }
//
//            LSHL, LSHR, LUSHR -> {
//                // shift amount is INT, value is LONG, push LONG
//                stack.safePop(INT)
//                stack.safePop(LONG)
//                stack.add(LONG)
//            }
//
//            LAND, LOR, LXOR -> {
//                // pop 2 longs, push 1 long
//                stack.safePop(LONG)
//                stack.safePop(LONG)
//                stack.add(LONG)
//            }
//
//            // ========== Conversions (simplified) ==========
//            I2L -> {
//                stack.safePop(INT)
//                stack.add(LONG)
//            }
//
//            I2F -> {
//                stack.safePop(INT)
//                stack.add(FLOAT)
//            }
//
//            I2D -> {
//                stack.safePop(INT)
//                stack.add(DOUBLE)
//            }
//
//            L2I -> {
//                stack.safePop(LONG)
//                stack.add(INT)
//            }
//
//            L2F -> {
//                stack.safePop(LONG)
//                stack.add(FLOAT)
//            }
//
//            L2D -> {
//                stack.safePop(LONG)
//                stack.add(DOUBLE)
//            }
//
//            F2I -> {
//                stack.safePop(FLOAT)
//                stack.add(INT)
//            }
//
//            F2L -> {
//                stack.safePop(FLOAT)
//                stack.add(LONG)
//            }
//
//            F2D -> {
//                stack.safePop(FLOAT)
//                stack.add(DOUBLE)
//            }
//
//            D2I -> {
//                stack.safePop(DOUBLE)
//                stack.add(INT)
//            }
//
//            D2L -> {
//                stack.safePop(DOUBLE)
//                stack.add(LONG)
//            }
//
//            D2F -> {
//                stack.safePop(DOUBLE)
//                stack.add(FLOAT)
//            }
//
//            I2B, I2C, I2S -> {
//                // JVM still treats these as int on the stack
//                stack.safePop(INT)
//                stack.add(INT)
//            }
//
//            // ========== Comparison ==========
//            LCMP -> {
//                stack.safePop(LONG)
//                stack.safePop(LONG)
//                stack.add(INT)
//            }
//
//            FCMPL, FCMPG -> {
//                stack.safePop(FLOAT)
//                stack.safePop(FLOAT)
//                stack.add(INT)
//            }
//
//            DCMPL, DCMPG -> {
//                stack.safePop(DOUBLE)
//                stack.safePop(DOUBLE)
//                stack.add(INT)
//            }
//
//            // ========== If/branch ==========
//            IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
//                // pop 1 int, no push
//                stack.safePop(INT)
//            }
//
//            IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
//                // pop 2 ints
//                stack.safePop(INT)
//                stack.safePop(INT)
//            }
//
//            IF_ACMPEQ, IF_ACMPNE -> {
//                // pop 2 refs
//                stack.safePop(OBJECT)
//                stack.safePop(OBJECT)
//            }
//
//            // ========== JSR / RET ==========
//            JSR -> {
//                // JSR pushes a return address (Stack.EntryType.RETURN_ADDRESS)
//                TODO()
//            }
//
//            RET -> {
//                // RET reads a return address from a local variable, no stack effect
//                // no push/pop from stack in standard usage
//            }
//
//            TABLESWITCH, LOOKUPSWITCH -> {
//                // Both pop one int (the switch key)
//                stack.safePop(INT)
//                // then jump, no push
//            }
//
//            // ========== Return ==========
//            IRETURN -> {
//                stack.safePop(INT)
//                // In a real flow analyzer, you’d note that control flow ends.
//            }
//
//            LRETURN -> stack.safePop(LONG)
//            FRETURN -> stack.safePop(FLOAT)
//            DRETURN -> stack.safePop(DOUBLE)
//            ARETURN -> stack.safePop(OBJECT)
//            RETURN -> {
//                // no pop
//            }
//
//            // ========== Field access ==========
//            GETSTATIC -> {
//                val fieldNode = node as FieldInsnNode
//                stack.add(Type.getType(fieldNode.desc).toJvmType())
//            }
//
//            PUTSTATIC -> {
//                val fieldNode = node as FieldInsnNode
//                stack.safePop(Type.getType(fieldNode.desc).toJvmType())
//            }
//
//            GETFIELD -> {
//                // pop object ref, push the field type
//                stack.safePop(OBJECT)
//
//                val fieldNode = node as FieldInsnNode
//                stack.add(Type.getType(fieldNode.desc).toJvmType())
//            }
//
//            PUTFIELD -> {
//                val fieldNode = node as FieldInsnNode
//                stack.safePop(Type.getType(fieldNode.desc).toJvmType())
//                // pop value, then object ref
//                stack.safePop(OBJECT)
//            }
//
//            // ========== Method calls ==========
//            INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> {
//                val invokeNode = node as MethodInsnNode
//
//                val method = Method(invokeNode.name, invokeNode.desc)
//
//                // Pop in reverse order
//                method.argumentTypes
//                    .reversed()
//                    .forEach {
//                        stack.safePop(it.toJvmType())
//                    }
//
//                // Pop object
//                if (node.opcode != INVOKESTATIC) {
//                    stack.safePop(OBJECT) // 'this'
//                }
//
//                if (method.returnType != Type.VOID_TYPE) {
//                    stack.add(method.returnType.toJvmType())
//                }
//            }
//
//            // TODO This needs testing
//            INVOKEDYNAMIC -> {
//                val invokeNode = node as InvokeDynamicInsnNode
//
//                val method = Method(invokeNode.name, invokeNode.desc)
//
//                // Pop in reverse order
//                method.argumentTypes
//                    .reversed()
//                    .forEach {
//                        stack.safePop(it.toJvmType())
//                    }
//
//                if (method.returnType != Type.VOID_TYPE) {
//                    stack.add(method.returnType.toJvmType())
//                }
//            }
//
//            // ========== Object creation / array / type checks ==========
//            NEW -> {
//                // push uninitialized object reference
//                stack.add(OBJECT)
//            }
//
//            NEWARRAY, ANEWARRAY -> {
//                // pop array size, push array reference
//                stack.safePop(INT)
//                stack.add(OBJECT)
//            }
//
//            ARRAYLENGTH -> {
//                // pop array ref, push int
//                stack.safePop(OBJECT)
//                stack.add(INT)
//            }
//
//            ATHROW -> {
//                // pop throwable ref, typical control flow ends
//                stack.safePop(OBJECT)
//            }
//
//            CHECKCAST -> {
//                // pop object ref, push (casted) object ref
//                stack.safePop(OBJECT)
//                stack.add(OBJECT)
//            }
//
//            INSTANCEOF -> {
//                // pop object ref, push int (0 or 1)
//                stack.safePop(OBJECT)
//                stack.add(INT)
//            }
//
//            MONITORENTER, MONITOREXIT -> {
//                // pop object ref, no push
//                stack.safePop(OBJECT)
//            }
//
//            MULTIANEWARRAY -> {
//                // pop N dimension ints, push array ref
//                // for demonstration, use node.arrayDimensions
//                repeat((node as MultiANewArrayInsnNode).dims) {
//                    stack.safePop(INT)
//                }
//                stack.add(OBJECT)
//            }
//
//            IFNULL, IFNONNULL -> {
//                // pop object ref
//                stack.safePop(OBJECT)
//            }
//
//            LDC -> {
//                val ldcNode = node as LdcInsnNode
//
//                when (ldcNode.cst) {
//                    is Int -> stack.add(INT)
//                    is Float -> stack.add(FLOAT)
//                    is Long -> stack.add(LONG)
//                    is Double -> stack.add(DOUBLE)
//                    else -> stack.add(OBJECT)
//                }
//            }
//
//            // ========== Catch-all ==========
//            else -> {
//                // Dont need to process this instruction
//            }
//        }
//    }
//
//    return stack
//}
//
////internal class StackValues {
////    private val stack: MutableList<Certainty> = ArrayList()
////
////    private val auxStack: MutableList<Certainty> = ArrayList()
////    private var auxDirection: Int = 0
////
////    var isCertain: Boolean = true
////        private set
////    var modifyStack: Boolean = true
////
////    fun add(certainty: Certainty) {
////        if (!certainty.isSure) isCertain = false
////
////        if (modifyStack) {
////            stack.add(certainty)
////        } else {
////            auxDirection += 1
////            auxStack.add(certainty)
////        }
////    }
////
////    fun safePop(expected: Certainty) {
////        if (modifyStack) {
////            if (stack.isEmpty()) {
////                throw IllegalStateException("Stack underflow.")
////            }
////            stack.removeLast()
////        } else {
////            auxDirection += 1
////            auxStack.add(expected)
////        }
////    }
////}
//
