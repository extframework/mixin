package dev.extframework.mixin.internal.analysis

import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.api.TypeSort.DOUBLE
import dev.extframework.mixin.api.TypeSort.FLOAT
import dev.extframework.mixin.api.TypeSort.INT
import dev.extframework.mixin.api.TypeSort.LONG
import dev.extframework.mixin.api.TypeSort.OBJECT
import org.objectweb.asm.Opcodes.AALOAD
import org.objectweb.asm.Opcodes.AASTORE
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ANEWARRAY
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ARRAYLENGTH
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.BALOAD
import org.objectweb.asm.Opcodes.BASTORE
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.CALOAD
import org.objectweb.asm.Opcodes.CASTORE
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.D2F
import org.objectweb.asm.Opcodes.D2I
import org.objectweb.asm.Opcodes.D2L
import org.objectweb.asm.Opcodes.DADD
import org.objectweb.asm.Opcodes.DALOAD
import org.objectweb.asm.Opcodes.DASTORE
import org.objectweb.asm.Opcodes.DCMPG
import org.objectweb.asm.Opcodes.DCMPL
import org.objectweb.asm.Opcodes.DCONST_0
import org.objectweb.asm.Opcodes.DCONST_1
import org.objectweb.asm.Opcodes.DDIV
import org.objectweb.asm.Opcodes.DLOAD
import org.objectweb.asm.Opcodes.DMUL
import org.objectweb.asm.Opcodes.DNEG
import org.objectweb.asm.Opcodes.DREM
import org.objectweb.asm.Opcodes.DRETURN
import org.objectweb.asm.Opcodes.DSTORE
import org.objectweb.asm.Opcodes.DSUB
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.DUP2
import org.objectweb.asm.Opcodes.DUP2_X1
import org.objectweb.asm.Opcodes.DUP2_X2
import org.objectweb.asm.Opcodes.DUP_X1
import org.objectweb.asm.Opcodes.DUP_X2
import org.objectweb.asm.Opcodes.F2D
import org.objectweb.asm.Opcodes.F2I
import org.objectweb.asm.Opcodes.F2L
import org.objectweb.asm.Opcodes.FADD
import org.objectweb.asm.Opcodes.FALOAD
import org.objectweb.asm.Opcodes.FASTORE
import org.objectweb.asm.Opcodes.FCMPG
import org.objectweb.asm.Opcodes.FCMPL
import org.objectweb.asm.Opcodes.FCONST_0
import org.objectweb.asm.Opcodes.FCONST_1
import org.objectweb.asm.Opcodes.FCONST_2
import org.objectweb.asm.Opcodes.FDIV
import org.objectweb.asm.Opcodes.FLOAD
import org.objectweb.asm.Opcodes.FMUL
import org.objectweb.asm.Opcodes.FNEG
import org.objectweb.asm.Opcodes.FREM
import org.objectweb.asm.Opcodes.FRETURN
import org.objectweb.asm.Opcodes.FSTORE
import org.objectweb.asm.Opcodes.FSUB
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.I2B
import org.objectweb.asm.Opcodes.I2C
import org.objectweb.asm.Opcodes.I2D
import org.objectweb.asm.Opcodes.I2F
import org.objectweb.asm.Opcodes.I2L
import org.objectweb.asm.Opcodes.I2S
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.IALOAD
import org.objectweb.asm.Opcodes.IAND
import org.objectweb.asm.Opcodes.IASTORE
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.ICONST_2
import org.objectweb.asm.Opcodes.ICONST_3
import org.objectweb.asm.Opcodes.ICONST_4
import org.objectweb.asm.Opcodes.ICONST_5
import org.objectweb.asm.Opcodes.ICONST_M1
import org.objectweb.asm.Opcodes.IDIV
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.IFGE
import org.objectweb.asm.Opcodes.IFGT
import org.objectweb.asm.Opcodes.IFLE
import org.objectweb.asm.Opcodes.IFLT
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.Opcodes.IFNONNULL
import org.objectweb.asm.Opcodes.IFNULL
import org.objectweb.asm.Opcodes.IF_ACMPEQ
import org.objectweb.asm.Opcodes.IF_ACMPNE
import org.objectweb.asm.Opcodes.IF_ICMPEQ
import org.objectweb.asm.Opcodes.IF_ICMPGE
import org.objectweb.asm.Opcodes.IF_ICMPGT
import org.objectweb.asm.Opcodes.IF_ICMPLE
import org.objectweb.asm.Opcodes.IF_ICMPLT
import org.objectweb.asm.Opcodes.IF_ICMPNE
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.IMUL
import org.objectweb.asm.Opcodes.INEG
import org.objectweb.asm.Opcodes.INSTANCEOF
import org.objectweb.asm.Opcodes.INVOKEDYNAMIC
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.IOR
import org.objectweb.asm.Opcodes.IREM
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.ISHL
import org.objectweb.asm.Opcodes.ISHR
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.ISUB
import org.objectweb.asm.Opcodes.IUSHR
import org.objectweb.asm.Opcodes.IXOR
import org.objectweb.asm.Opcodes.JSR
import org.objectweb.asm.Opcodes.L2D
import org.objectweb.asm.Opcodes.L2F
import org.objectweb.asm.Opcodes.L2I
import org.objectweb.asm.Opcodes.LADD
import org.objectweb.asm.Opcodes.LALOAD
import org.objectweb.asm.Opcodes.LAND
import org.objectweb.asm.Opcodes.LASTORE
import org.objectweb.asm.Opcodes.LCMP
import org.objectweb.asm.Opcodes.LCONST_0
import org.objectweb.asm.Opcodes.LCONST_1
import org.objectweb.asm.Opcodes.LDC
import org.objectweb.asm.Opcodes.LDIV
import org.objectweb.asm.Opcodes.LLOAD
import org.objectweb.asm.Opcodes.LMUL
import org.objectweb.asm.Opcodes.LNEG
import org.objectweb.asm.Opcodes.LOOKUPSWITCH
import org.objectweb.asm.Opcodes.LOR
import org.objectweb.asm.Opcodes.LREM
import org.objectweb.asm.Opcodes.LRETURN
import org.objectweb.asm.Opcodes.LSHL
import org.objectweb.asm.Opcodes.LSHR
import org.objectweb.asm.Opcodes.LSTORE
import org.objectweb.asm.Opcodes.LSUB
import org.objectweb.asm.Opcodes.LUSHR
import org.objectweb.asm.Opcodes.LXOR
import org.objectweb.asm.Opcodes.MONITORENTER
import org.objectweb.asm.Opcodes.MONITOREXIT
import org.objectweb.asm.Opcodes.MULTIANEWARRAY
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.POP2
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.RET
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.SALOAD
import org.objectweb.asm.Opcodes.SASTORE
import org.objectweb.asm.Opcodes.SIPUSH
import org.objectweb.asm.Opcodes.SWAP
import org.objectweb.asm.Opcodes.TABLESWITCH
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.math.exp

public data class SimulatedFrame(
    val stack: List<JvmValueRef>,
    val locals: List<JvmValueRef>
)

public fun analyzeFrames(
    instructions: InsnList,
    target: AbstractInsnNode,
    initial: SimulatedFrame = SimulatedFrame(listOf(), listOf())
): SimulatedFrame {
    val controlFlow = CodeFlowAnalyzer.buildCFG(
        CodeFlowAnalyzer.buildFullFlowGraph(instructions),
    )

    val dominator = CodeFlowAnalyzer.buildDominator(controlFlow)

    val domPath = dominator.findPathTo(target)

    val allInstructions = domPath.flatMap {
        it.node.instructions
    }

    val instructionsUntil = allInstructions.takeWhile {
        it != target
    }

    return computeFrame(
        initial,
        instructionsUntil,
    )
}

internal fun CodeFlowAnalyzer.DominatorNode.findPathTo(
    target: AbstractInsnNode
): List<CodeFlowAnalyzer.DominatorNode> {
    if (node.instructions.contains(target)) return listOf(this)

    val found = children
        .map { it.findPathTo(target) }
        .find { it.isNotEmpty() }


    return if (found != null) (listOf(this) + found) else emptyList()
}

internal fun computeFrame(
    initial: SimulatedFrame,
    targetInstructions: List<AbstractInsnNode>
): SimulatedFrame {
    val stack = initial.stack.toMutableList()
    val locals = initial.locals.toMutableList()

    for (node in targetInstructions) {
        when (node.opcode) {

            // ========== Constants ==========
            ACONST_NULL -> {
                stack.add(NullValueRef)
            }

            ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
            BIPUSH, SIPUSH -> {
                stack.add(SortValueRef(INT))
            }

            LCONST_0, LCONST_1 -> {
                stack.add(SortValueRef(LONG))
            }

            FCONST_0, FCONST_1, FCONST_2 -> {
                stack.add(SortValueRef(FLOAT))
            }

            DCONST_0, DCONST_1 -> {
                stack.add(SortValueRef(DOUBLE))
            }

            // ========== Load instructions ==========
            ILOAD -> stack.add(SortValueRef(INT))
            LLOAD -> stack.add(SortValueRef(LONG))
            FLOAD -> stack.add(SortValueRef(FLOAT))
            DLOAD -> stack.add(SortValueRef(DOUBLE))
            ALOAD -> {
                node as VarInsnNode

                stack.add(locals[node.`var`])
            }

            // ========== Array load ==========
            IALOAD -> {
                stack.safePop(INT)    // index
                stack.safePop(OBJECT) // array ref
                stack.add(SortValueRef(INT))
            }

            LALOAD -> {
                stack.safePop(INT)
                stack.safePop(OBJECT)
                stack.add(SortValueRef(LONG))
            }

            FALOAD -> {
                stack.safePop(INT)
                stack.safePop(OBJECT)
                stack.add(SortValueRef(FLOAT))
            }

            DALOAD -> {
                stack.safePop(INT)
                stack.safePop(OBJECT)
                stack.add(SortValueRef(DOUBLE))
            }

            AALOAD -> {
                stack.safePop(INT)
                stack.safePop(OBJECT)
                stack.add(SortValueRef(OBJECT))
            }

            BALOAD, CALOAD, SALOAD -> {
                stack.safePop(INT)    // index
                stack.safePop(OBJECT) // array ref
                // On the JVM stack, byte/char/short → int
                stack.add(SortValueRef(INT))
            }

            // ========== Store instructions ==========
            ISTORE -> {
                node as VarInsnNode
                locals.add(node.`var`, stack.safePop(INT))
            }

            LSTORE -> {
                node as VarInsnNode
                locals.add(node.`var`, stack.safePop(LONG))
            }

            FSTORE -> {
                node as VarInsnNode
                locals.add(node.`var`, stack.safePop(FLOAT))
            }

            DSTORE -> {
                node as VarInsnNode
                locals.add(node.`var`, stack.safePop(DOUBLE))
            }

            ASTORE -> {
                node as VarInsnNode
                locals.safeSet(node.`var`, stack.safePop(OBJECT))
            }

            // ========== Array store ==========
            IASTORE -> {
                stack.safePop(INT)    // value
                stack.safePop(INT)    // index
                stack.safePop(OBJECT) // array ref
            }

            LASTORE -> {
                stack.safePop(LONG)
                stack.safePop(INT)
                stack.safePop(OBJECT)
            }

            FASTORE -> {
                stack.safePop(FLOAT)
                stack.safePop(INT)
                stack.safePop(OBJECT)
            }

            DASTORE -> {
                stack.safePop(DOUBLE)
                stack.safePop(INT)
                stack.safePop(OBJECT)
            }

            AASTORE -> {
                stack.safePop(OBJECT) // value
                stack.safePop(INT)    // index
                stack.safePop(OBJECT) // array ref
            }

            BASTORE, CASTORE, SASTORE -> {
                stack.safePop(INT)
                stack.safePop(INT)
                stack.safePop(OBJECT)
            }

            // ========== Stack management ==========
            POP -> {
                stack.removeLast()
            }

            POP2 -> {
                // If top is category-2 (LONG/DOUBLE), pop just one
                // else pop two category-1
                if (stack.isEmpty()) {
                    throw IllegalStateException("Stack underflow for POP2")
                }
                val top = stack.removeAt(stack.lastIndex)
                if (top != SortValueRef(LONG) && top != SortValueRef(DOUBLE)) {
                    // pop one more
                    stack.removeLast()
                }
            }

            DUP -> {
                if (stack.isEmpty()) {
                    throw IllegalStateException("Stack underflow for DUP")
                }
                val top = stack.last()
                stack.add(top)
            }

            DUP_X1 -> {
                // [top2, top1] -> [top1, top2, top1]
                if (stack.size < 2) {
                    throw IllegalStateException("Stack underflow for DUP_X1")
                }
                val top1 = stack.removeAt(stack.lastIndex)
                val top2 = stack.removeAt(stack.lastIndex)
                // push in order top1, top2, top1
                stack.add(top1)
                stack.add(top2)
                stack.add(top1)
            }

            DUP_X2 -> {
                // This depends on whether the second item is category-1 or category-2
                // Some logic:
                if (stack.size < 2) {
                    throw IllegalStateException("Stack underflow for DUP_X2")
                }
                val top1 = stack.removeAt(stack.lastIndex)
                val top2 = stack.removeAt(stack.lastIndex)

                if (top2 == SortValueRef(LONG) || top2 == SortValueRef(DOUBLE)) {
                    // [cat2, cat1] -> [cat1, cat2, cat1]
                    stack.add(top1)
                    stack.add(top2)
                    stack.add(top1)
                } else {
                    // We need a third item
                    if (stack.isEmpty()) {
                        throw IllegalStateException("Stack underflow for DUP_X2 - need 3rd item")
                    }
                    val top3 = stack.removeAt(stack.lastIndex)
                    // [top3, top2, top1] => [top1, top3, top2, top1]
                    stack.add(top1)
                    stack.add(top3)
                    stack.add(top2)
                    stack.add(top1)
                }
            }

            DUP2 -> {
                // If top is category-2 => duplicate it
                // If top is category-1 => need to duplicate top two
                if (stack.isEmpty()) {
                    throw IllegalStateException("Stack underflow for DUP2")
                }
                val t1 = stack.removeAt(stack.lastIndex)
                if (t1 == SortValueRef(LONG) || t1 == SortValueRef(DOUBLE)) {
                    // Category-2 -> push t1 twice
                    stack.add(t1)
                    stack.add(t1)
                } else {
                    // Need second top
                    if (stack.isEmpty()) {
                        throw IllegalStateException("Stack underflow for DUP2 (need second item)")
                    }
                    val t2 = stack.removeAt(stack.lastIndex)
                    // push [t2, t1] again
                    stack.add(t2)
                    stack.add(t1)
                    stack.add(t2)
                    stack.add(t1)
                }
            }

            DUP2_X1, DUP2_X2 -> {
                // Rarely used, more complex duplication (category-2 vs category-1 combos)
                // Implement similarly to DUP_X1/X2 but for two or cat-2 items
                TODO("Implement DUP2_X1 / DUP2_X2 if needed")
            }

            SWAP -> {
                // Swap top two category-1 values
                if (stack.size < 2) {
                    throw IllegalStateException("Stack underflow for SWAP")
                }
                val t1 = stack.removeAt(stack.lastIndex)
                val t2 = stack.removeAt(stack.lastIndex)
                // Must both be category-1
                if (t1 == SortValueRef(LONG) || t1 == SortValueRef(DOUBLE) ||
                    t2 == SortValueRef(LONG) || t2 == SortValueRef(DOUBLE)
                ) {
                    throw IllegalStateException("SWAP requires two category-1 values")
                }
                stack.add(t1)
                stack.add(t2)
            }

            // ========== Arithmetic ==========
            IADD, ISUB, IMUL, IDIV, IREM -> {
                stack.safePop(INT)
                stack.safePop(INT)
                stack.add(SortValueRef(INT))
            }

            LADD, LSUB, LMUL, LDIV, LREM -> {
                stack.safePop(LONG)
                stack.safePop(LONG)
                stack.add(SortValueRef(LONG))
            }

            FADD, FSUB, FMUL, FDIV, FREM -> {
                stack.safePop(FLOAT)
                stack.safePop(FLOAT)
                stack.add(SortValueRef(FLOAT))
            }

            DADD, DSUB, DMUL, DDIV, DREM -> {
                stack.safePop(DOUBLE)
                stack.safePop(DOUBLE)
                stack.add(SortValueRef(DOUBLE))
            }

            // ========== Negate / shift / bitwise / iinc, etc. ==========
            INEG -> {
                stack.safePop(INT)
                stack.add(SortValueRef(INT))
            }

            LNEG -> {
                stack.safePop(LONG)
                stack.add(SortValueRef(LONG))
            }

            FNEG -> {
                stack.safePop(FLOAT)
                stack.add(SortValueRef(FLOAT))
            }

            DNEG -> {
                stack.safePop(DOUBLE)
                stack.add(SortValueRef(DOUBLE))
            }

            ISHL, ISHR, IUSHR, IAND, IOR, IXOR -> {
                // All take 2 ints, push 1 int
                stack.safePop(INT)
                stack.safePop(INT)
                stack.add(SortValueRef(INT))
            }

            LSHL, LSHR, LUSHR -> {
                // shift amount is INT, value is LONG, push LONG
                stack.safePop(INT)
                stack.safePop(LONG)
                stack.add(SortValueRef(LONG))
            }

            LAND, LOR, LXOR -> {
                // pop 2 longs, push 1 long
                stack.safePop(LONG)
                stack.safePop(LONG)
                stack.add(SortValueRef(LONG))
            }

            // ========== Conversions (simplified) ==========
            I2L -> {
                stack.safePop(INT)
                stack.add(SortValueRef(LONG))
            }

            I2F -> {
                stack.safePop(INT)
                stack.add(SortValueRef(FLOAT))
            }

            I2D -> {
                stack.safePop(INT)
                stack.add(SortValueRef(DOUBLE))
            }

            L2I -> {
                stack.safePop(LONG)
                stack.add(SortValueRef(INT))
            }

            L2F -> {
                stack.safePop(LONG)
                stack.add(SortValueRef(FLOAT))
            }

            L2D -> {
                stack.safePop(LONG)
                stack.add(SortValueRef(DOUBLE))
            }

            F2I -> {
                stack.safePop(FLOAT)
                stack.add(SortValueRef(INT))
            }

            F2L -> {
                stack.safePop(FLOAT)
                stack.add(SortValueRef(LONG))
            }

            F2D -> {
                stack.safePop(FLOAT)
                stack.add(SortValueRef(DOUBLE))
            }

            D2I -> {
                stack.safePop(DOUBLE)
                stack.add(SortValueRef(INT))
            }

            D2L -> {
                stack.safePop(DOUBLE)
                stack.add(SortValueRef(LONG))
            }

            D2F -> {
                stack.safePop(DOUBLE)
                stack.add(SortValueRef(FLOAT))
            }

            I2B, I2C, I2S -> {
                // JVM still treats these as int on the stack
                stack.safePop(INT)
                stack.add(SortValueRef(INT))
            }

            // ========== Comparison ==========
            LCMP -> {
                stack.safePop(LONG)
                stack.safePop(LONG)
                stack.add(SortValueRef(INT))
            }

            FCMPL, FCMPG -> {
                stack.safePop(FLOAT)
                stack.safePop(FLOAT)
                stack.add(SortValueRef(INT))
            }

            DCMPL, DCMPG -> {
                stack.safePop(DOUBLE)
                stack.safePop(DOUBLE)
                stack.add(SortValueRef(INT))
            }

            // ========== If/branch ==========
            IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                // pop 1 int, no push
                stack.safePop(INT)
            }

            IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                // pop 2 ints
                stack.safePop(INT)
                stack.safePop(INT)
            }

            IF_ACMPEQ, IF_ACMPNE -> {
                // pop 2 refs
                stack.safePop(OBJECT)
                stack.safePop(OBJECT)
            }

            // ========== JSR / RET ==========
            JSR -> {
                // JSR pushes a return address (Stack.EntryType.RETURN_ADDRESS)
                TODO()
            }

            RET -> {
                // RET reads a return address from a local variable, no stack effect
                // no push/pop from stack in standard usage
            }

            TABLESWITCH, LOOKUPSWITCH -> {
                // Both pop one int (the switch key)
                stack.safePop(INT)
                // then jump, no push
            }

            // ========== Return ==========
            IRETURN -> {
                stack.safePop(INT)
                // In a real flow analyzer, you’d note that control flow ends.
            }

            LRETURN -> stack.safePop(LONG)
            FRETURN -> stack.safePop(FLOAT)
            DRETURN -> stack.safePop(DOUBLE)
            ARETURN -> stack.safePop(OBJECT)
            RETURN -> {
                // no pop
            }

            // ========== Field access ==========
            GETSTATIC -> {
                val fieldNode = node as FieldInsnNode
                stack.add(Type.getType(fieldNode.desc).toValueRef())
            }

            PUTSTATIC -> {
                val fieldNode = node as FieldInsnNode
                stack.safePop(Type.getType(fieldNode.desc).toValueRef())
            }

            GETFIELD -> {
                // pop object ref, push the field type
                stack.safePop(OBJECT)

                val fieldNode = node as FieldInsnNode
                stack.add(Type.getType(fieldNode.desc).toValueRef())
            }

            PUTFIELD -> {
                val fieldNode = node as FieldInsnNode
                stack.safePop(Type.getType(fieldNode.desc).toValueRef())
                // pop value, then object ref
                stack.safePop(OBJECT)
            }

            // ========== Method calls ==========
            INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> {
                val invokeNode = node as MethodInsnNode

                val method = Method(invokeNode.name, invokeNode.desc)

                // Pop in reverse order
                method.argumentTypes
                    .reversed()
                    .forEach {
                        stack.safePop(it.toValueRef())
                    }

                // Pop object
                if (node.opcode != INVOKESTATIC) {
                    stack.safePop(OBJECT) // 'this'
                }

                if (method.returnType != Type.VOID_TYPE) {
                    stack.add(method.returnType.toValueRef())
                }
            }

            // TODO This needs testing
            INVOKEDYNAMIC -> {
                val invokeNode = node as InvokeDynamicInsnNode

                val method = Method(invokeNode.name, invokeNode.desc)

                // Pop in reverse order
                method.argumentTypes
                    .reversed()
                    .forEach {
                        stack.safePop(it.toJvmType())
                    }

                if (method.returnType != Type.VOID_TYPE) {
                    stack.add(method.returnType.toValueRef())
                }
            }

            // ========== Object creation / array / type checks ==========
            NEW -> {
                node as TypeInsnNode
                // push uninitialized object reference
                stack.add(ObjectValueRef(Type.getObjectType(node.desc)))
            }

            NEWARRAY -> {
                // See https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.newarray
                node as IntInsnNode

                stack.safePop(INT)
                stack.add(
                    ObjectValueRef(
                        Type.getType(
                            "[" + (when (node.operand) {
                                4 -> Type.BOOLEAN_TYPE
                                5 -> Type.CHAR_TYPE
                                6 -> Type.FLOAT_TYPE
                                7 -> Type.DOUBLE_TYPE
                                8 -> Type.BYTE_TYPE
                                9 -> Type.SHORT_TYPE
                                10 -> Type.INT_TYPE
                                11 -> Type.LONG_TYPE
                                else -> throw Exception("")
                            }.descriptor)
                        )
                    )
                )
            }

            ANEWARRAY -> {
                node as TypeInsnNode
                // pop array size, push array reference
                stack.safePop(INT)
                stack.add(ObjectValueRef(Type.getObjectType("[" + Type.getObjectType(node.desc).descriptor)))
            }

            ARRAYLENGTH -> {
                // pop array ref, push int
                stack.safePop(OBJECT)
                stack.add(SortValueRef(INT))
            }

            ATHROW -> {
                // pop throwable ref, typical control flow ends
                stack.safePop(OBJECT)
            }

            CHECKCAST -> {
                node as TypeInsnNode
                // pop object ref, push (casted) object ref
                stack.safePop(OBJECT)
                stack.add(ObjectValueRef(Type.getObjectType(node.desc)))
            }

            INSTANCEOF -> {
                // pop object ref, push int (0 or 1)
                stack.safePop(OBJECT)
                stack.add(SortValueRef(INT))
            }

            MONITORENTER, MONITOREXIT -> {
                // pop object ref, no push
                stack.safePop(OBJECT)
            }

            MULTIANEWARRAY -> {
                // pop N dimension ints, push array ref
                // for demonstration, use node.arrayDimensions
                repeat((node as MultiANewArrayInsnNode).dims) {
                    stack.safePop(INT)
                }
                stack.add(ObjectValueRef(Type.getType("[" + node.desc)))
            }

            IFNULL, IFNONNULL -> {
                // pop object ref
                stack.safePop(OBJECT)
            }

            LDC -> {
                val cst = (node as LdcInsnNode).cst
                when (cst) {
                    is Int -> stack.add(SortValueRef(INT))
                    is Float -> stack.add(SortValueRef(FLOAT))
                    is Long -> stack.add(SortValueRef(LONG))
                    is Double -> stack.add(SortValueRef(DOUBLE))
                    is String -> stack.add(ObjectValueRef(Type.getType(String::class.java)))
                    is Type -> stack.add(ObjectValueRef(cst))
                }
            }

            // ========== Catch-all ==========
            else -> {
                // Dont need to process this instruction
            }
        }
    }

    return SimulatedFrame(
        stack, locals
    )
}

internal fun Type.toJvmType(): TypeSort {
    return when (this.sort) {
        Type.INT, Type.SHORT, Type.BYTE, Type.CHAR, Type.BOOLEAN -> return INT
        Type.FLOAT -> return FLOAT
        Type.LONG -> return LONG
        Type.DOUBLE -> return DOUBLE
        Type.ARRAY, Type.OBJECT -> return OBJECT
        else -> throw Exception("Unsupported type $this")
    }
}

private fun MutableList<JvmValueRef>.safePop(
    expected: TypeSort
): JvmValueRef {
    return safePop(SortValueRef(expected))
}

private fun MutableList<JvmValueRef>.safePop(
    expected: JvmValueRef
): JvmValueRef {
    if (this.isEmpty()) {
        throw IllegalStateException("Stack underflow.")
    }
    val top = removeAt(this.lastIndex)

    // TODO Literally the same as ClassWriter in ASM, we need super type information to
    //   accurately do this.

    val areBothObjects = expected is ObjectValueRef && top is ObjectValueRef

    val expectsAnalogousObjectAndIs = expected is SortValueRef &&
            expected.sort == OBJECT &&
            top is ObjectValueRef

    val isNullAndExpectsObject = expected is ObjectValueRef && top is NullValueRef

    if (top != expected && !areBothObjects && !expectsAnalogousObjectAndIs && !isNullAndExpectsObject) {
        throw IllegalStateException(
            "Expected top of stack to be $expected but found $top."
        )
    }
    return top
}

private fun <T> MutableList<T>.safeSet(
    index: Int,
    value: T
) {
    if (size == index) {
        add(value)
    } else if (size > index) {
        set(index, value)
    } else {
        throw IndexOutOfBoundsException()
    }
}
