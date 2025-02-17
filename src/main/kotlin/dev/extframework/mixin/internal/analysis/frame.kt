package dev.extframework.mixin.internal.analysis

import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.internal.analysis.CodeFlowAnalyzer.CFGNode
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AnalyzerAdapter
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*

public data class SimulatedFrame(
    // Items on the top are last
    val stack: List<JvmValueRef>,
    val locals: Map<Int, JvmValueRef>
) {
    public constructor(stack: List<JvmValueRef>, locals: List<JvmValueRef>) : this(
        stack,
        locals.withIndex().associate { it.index to it.value })
}

// TODO determine if this is better or worse than using ASMs analyzer.
public fun analyzeFrames(
    target: AbstractInsnNode,
//    tryCatchBlocks: List<TryCatchBlockNode>,
    initial: SimulatedFrame = SimulatedFrame(listOf(), mapOf())
): SimulatedFrame {
    val adapter = AnalyzerAdapter(
        "",
        ACC_PUBLIC,
        "",
        "()V",
        null
    )

    val (stack, locals) = initial.toAsmFrame()
    adapter.stack = stack
    adapter.locals = locals

    // CPU cycles over memory
    val allInstructions = ArrayList<AbstractInsnNode>(20)

    var initialFrame: SimulatedFrame? = null
    var next: AbstractInsnNode? = target

    // Reverse until we find a frame node or the beginning of this section
    while (initialFrame == null) {
        if (next == null) initialFrame = initial
        else if (next is FrameNode) {
            initialFrame = next.toSimulatedFrame()
        } else {
            next = next.previous
        }
        if (next != null) { // Ignores target
            // Add from beginning, incurs slight toll when resizing but less than reiterating the whole list.
            allInstructions.add(0, next)
        }
    }

    visitAnalyzer(
        adapter,
        allInstructions
    )

//    return computeFrame(
//        initial,allInstructions
//    )

    return SimulatedFrame(
        adapter.stack?.mapNotNull { it.toJvmRef() } ?: listOf(),
        adapter.locals
            ?.withIndex()
            ?.map { it.index to it.value.toJvmRef() }
            ?.filterNot { it.second == null }
            ?.toMap() as? Map<Int, JvmValueRef> ?: mapOf()
    )
//    return computeFrame(
//        initialFrame,
//        tryCatchBlocks,
//        allInstructions
//    )
}

//internal fun CFGNode.find(
//    target: AbstractInsnNode
//): CFGNode? {
//    val perimeter = ArrayList<CFGNode>()
//    val visited = HashSet<CFGNode>()
//    perimeter.add(this)
//
//    while (!perimeter.isEmpty()) {
//        val current = perimeter.removeAt(0)
//        if (!visited.add(current)) continue
//
//        if (current.instructions.contains(target)) return current
//
//        for (node in current.exitsBy) {
//            perimeter.add(node)
//        }
//    }
//
//    return null
//}

internal fun visitAnalyzer(
    analyzerAdapter: AnalyzerAdapter,
    insn: List<AbstractInsnNode>
) {
    for (node in insn) {
        when (node) {
            is FrameNode -> {
                analyzerAdapter.visitFrame(
                    node.type,
                    node.local.size,
                    node.local.toTypedArray(),
                    node.stack.size,
                    node.stack.toTypedArray()
                )
            }
            is InsnNode -> {
                analyzerAdapter.visitInsn(
                    node.opcode,
                )
            }
            is IntInsnNode -> {
                analyzerAdapter.visitIntInsn(node.opcode, node.operand)
            }
            is VarInsnNode -> {
                analyzerAdapter.visitVarInsn(node.opcode, node.`var`)
            }
            is TypeInsnNode -> {
                analyzerAdapter.visitTypeInsn(node.opcode, node.desc)
            }
            is FieldInsnNode -> {
                analyzerAdapter.visitFieldInsn(node.opcode, node.owner, node.name, node.desc)
            }
            is MethodInsnNode -> {
                analyzerAdapter.visitMethodInsn(node.opcode, node.owner, node.name, node.desc, node.itf)
            }
            is InvokeDynamicInsnNode -> {
                analyzerAdapter.visitInvokeDynamicInsn(node.name, node.desc, node.bsm, node.bsmArgs)
            }
            is JumpInsnNode -> {
                analyzerAdapter.visitJumpInsn(node.opcode, node.label.label)
            }
            is LabelNode -> {
                analyzerAdapter.visitLabel(node.label)
            }
            is LdcInsnNode -> {
                analyzerAdapter.visitLdcInsn(node.cst)
            }
            is IincInsnNode -> {
                analyzerAdapter.visitIincInsn(node.`var`, node.incr)
            }
            is TableSwitchInsnNode -> {
                analyzerAdapter.visitTableSwitchInsn(node.min, node.max, node.dflt.label, *node.labels.map { it.label }.toTypedArray())
            }
            is LookupSwitchInsnNode -> {
                analyzerAdapter.visitLookupSwitchInsn(node.dflt.label,node.keys.toIntArray(),node.labels.map { it.label }.toTypedArray())
            }
            is MultiANewArrayInsnNode -> {
                analyzerAdapter.visitMultiANewArrayInsn(node.desc, node.dims)
            }
        }
    }
}

internal fun computeFrame(
    initial: SimulatedFrame,
//    tryCatchBlocks: List<TryCatchBlockNode>,
    instructions: List<AbstractInsnNode>
): SimulatedFrame {
    val stack = initial.stack.toMutableList()
    val locals = initial.locals.toMutableMap()

//    val exceptionHandlers = tryCatchBlocks.associateBy {
//        it.start
//    }

    for (node in instructions) {
//        val handler = exceptionHandlers[node]
//        if (handler != null) {
//            val type = if (handler.type == null) {
//                ObjectValueRef(Type.getType(Throwable::class.java))
//            } else {
//                ObjectValueRef(Type.getObjectType(handler.type))
//            }
//            stack.add(type)
//        }

        when (node.opcode) {

            // ========== Constants ==========
            ACONST_NULL -> {
                stack.add(NullValueRef)
            }

            ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
            BIPUSH, SIPUSH -> {
                stack.add(SortValueRef(TypeSort.INT))
            }

            LCONST_0, LCONST_1 -> {
                stack.add(SortValueRef(TypeSort.LONG))
            }

            FCONST_0, FCONST_1, FCONST_2 -> {
                stack.add(SortValueRef(TypeSort.FLOAT))
            }

            DCONST_0, DCONST_1 -> {
                stack.add(SortValueRef(TypeSort.DOUBLE))
            }

            // ========== Load instructions ==========
            ILOAD -> stack.add(SortValueRef(TypeSort.INT))
            LLOAD -> stack.add(SortValueRef(TypeSort.LONG))
            FLOAD -> stack.add(SortValueRef(TypeSort.FLOAT))
            DLOAD -> stack.add(SortValueRef(TypeSort.DOUBLE))
            ALOAD -> {
                node as VarInsnNode

                // TODO better error message
                stack.add(locals[node.`var`] ?: throw Exception("Type not on stack?"))
            }

            // ========== Array load ==========
            IALOAD -> {
                stack.safePop(TypeSort.INT)    // index
                stack.safePop(TypeSort.OBJECT) // array ref
                stack.add(SortValueRef(TypeSort.INT))
            }

            LALOAD -> {
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            FALOAD -> {
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
                stack.add(SortValueRef(TypeSort.FLOAT))
            }

            DALOAD -> {
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
                stack.add(SortValueRef(TypeSort.DOUBLE))
            }

            AALOAD -> {
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
                stack.add(SortValueRef(TypeSort.OBJECT))
            }

            BALOAD, CALOAD, SALOAD -> {
                stack.safePop(TypeSort.INT)    // index
                stack.safePop(TypeSort.OBJECT) // array ref
                // On the JVM stack, byte/char/short → int
                stack.add(SortValueRef(TypeSort.INT))
            }

            // ========== Store instructions ==========
            ISTORE -> {
                node as VarInsnNode
                locals[node.`var`] = stack.safePop(TypeSort.INT)
            }

            LSTORE -> {
                node as VarInsnNode
                locals[node.`var`] = stack.safePop(TypeSort.LONG)
            }

            FSTORE -> {
                node as VarInsnNode
                locals[node.`var`] = stack.safePop(TypeSort.FLOAT)
            }

            DSTORE -> {
                node as VarInsnNode
                locals[node.`var`] = stack.safePop(TypeSort.DOUBLE)
            }

            ASTORE -> {
                node as VarInsnNode
                locals[node.`var`] = stack.safePop(TypeSort.OBJECT)
            }

            // ========== Array store ==========
            IASTORE -> {
                stack.safePop(TypeSort.INT)    // value
                stack.safePop(TypeSort.INT)    // index
                stack.safePop(TypeSort.OBJECT) // array ref
            }

            LASTORE -> {
                stack.safePop(TypeSort.LONG)
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
            }

            FASTORE -> {
                stack.safePop(TypeSort.FLOAT)
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
            }

            DASTORE -> {
                stack.safePop(TypeSort.DOUBLE)
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
            }

            AASTORE -> {
                stack.safePop(TypeSort.OBJECT) // value
                stack.safePop(TypeSort.INT)    // index
                stack.safePop(TypeSort.OBJECT) // array ref
            }

            BASTORE, CASTORE, SASTORE -> {
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.OBJECT)
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
                if (top != SortValueRef(TypeSort.LONG) && top != SortValueRef(TypeSort.DOUBLE)) {
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

                if (top2 == SortValueRef(TypeSort.LONG) || top2 == SortValueRef(TypeSort.DOUBLE)) {
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
                if (t1 == SortValueRef(TypeSort.LONG) || t1 == SortValueRef(TypeSort.DOUBLE)) {
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
                if (t1 == SortValueRef(TypeSort.LONG) || t1 == SortValueRef(TypeSort.DOUBLE) ||
                    t2 == SortValueRef(TypeSort.LONG) || t2 == SortValueRef(TypeSort.DOUBLE)
                ) {
                    throw IllegalStateException("SWAP requires two category-1 values")
                }
                stack.add(t1)
                stack.add(t2)
            }

            // ========== Arithmetic ==========
            IADD, ISUB, IMUL, IDIV, IREM -> {
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.INT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            LADD, LSUB, LMUL, LDIV, LREM -> {
                stack.safePop(TypeSort.LONG)
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            FADD, FSUB, FMUL, FDIV, FREM -> {
                stack.safePop(TypeSort.FLOAT)
                stack.safePop(TypeSort.FLOAT)
                stack.add(SortValueRef(TypeSort.FLOAT))
            }

            DADD, DSUB, DMUL, DDIV, DREM -> {
                stack.safePop(TypeSort.DOUBLE)
                stack.safePop(TypeSort.DOUBLE)
                stack.add(SortValueRef(TypeSort.DOUBLE))
            }

            // ========== Negate / shift / bitwise / iinc, etc. ==========
            INEG -> {
                stack.safePop(TypeSort.INT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            LNEG -> {
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            FNEG -> {
                stack.safePop(TypeSort.FLOAT)
                stack.add(SortValueRef(TypeSort.FLOAT))
            }

            DNEG -> {
                stack.safePop(TypeSort.DOUBLE)
                stack.add(SortValueRef(TypeSort.DOUBLE))
            }

            ISHL, ISHR, IUSHR, IAND, IOR, IXOR -> {
                // All take 2 ints, push 1 int
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.INT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            LSHL, LSHR, LUSHR -> {
                // shift amount is TypeSort.INT, value is LONG, push LONG
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            LAND, LOR, LXOR -> {
                // pop 2 longs, push 1 long
                stack.safePop(TypeSort.LONG)
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            // ========== Conversions (simplified) ==========
            I2L -> {
                stack.safePop(TypeSort.INT)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            I2F -> {
                stack.safePop(TypeSort.INT)
                stack.add(SortValueRef(TypeSort.FLOAT))
            }

            I2D -> {
                stack.safePop(TypeSort.INT)
                stack.add(SortValueRef(TypeSort.DOUBLE))
            }

            L2I -> {
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.INT))
            }

            L2F -> {
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.FLOAT))
            }

            L2D -> {
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.DOUBLE))
            }

            F2I -> {
                stack.safePop(TypeSort.FLOAT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            F2L -> {
                stack.safePop(TypeSort.FLOAT)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            F2D -> {
                stack.safePop(TypeSort.FLOAT)
                stack.add(SortValueRef(TypeSort.DOUBLE))
            }

            D2I -> {
                stack.safePop(TypeSort.DOUBLE)
                stack.add(SortValueRef(TypeSort.INT))
            }

            D2L -> {
                stack.safePop(TypeSort.DOUBLE)
                stack.add(SortValueRef(TypeSort.LONG))
            }

            D2F -> {
                stack.safePop(TypeSort.DOUBLE)
                stack.add(SortValueRef(TypeSort.FLOAT))
            }

            I2B, I2C, I2S -> {
                // JVM still treats these as int on the stack
                stack.safePop(TypeSort.INT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            // ========== Comparison ==========
            LCMP -> {
                stack.safePop(TypeSort.LONG)
                stack.safePop(TypeSort.LONG)
                stack.add(SortValueRef(TypeSort.INT))
            }

            FCMPL, FCMPG -> {
                stack.safePop(TypeSort.FLOAT)
                stack.safePop(TypeSort.FLOAT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            DCMPL, DCMPG -> {
                stack.safePop(TypeSort.DOUBLE)
                stack.safePop(TypeSort.DOUBLE)
                stack.add(SortValueRef(TypeSort.INT))
            }

            // ========== If/branch ==========
            IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                // pop 1 int, no push
                stack.safePop(TypeSort.INT)
            }

            IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                // pop 2 ints
                stack.safePop(TypeSort.INT)
                stack.safePop(TypeSort.INT)
            }

            IF_ACMPEQ, IF_ACMPNE -> {
                // pop 2 refs
                stack.safePop(TypeSort.OBJECT)
                stack.safePop(TypeSort.OBJECT)
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
                stack.safePop(TypeSort.INT)
                // then jump, no push
            }

            // ========== Return ==========
            IRETURN -> {
                stack.safePop(TypeSort.INT)
                // In a real flow analyzer, you’d note that control flow ends.
            }

            LRETURN -> stack.safePop(TypeSort.LONG)
            FRETURN -> stack.safePop(TypeSort.FLOAT)
            DRETURN -> stack.safePop(TypeSort.DOUBLE)
            ARETURN -> stack.safePop(TypeSort.OBJECT)
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
                stack.safePop(TypeSort.OBJECT)

                val fieldNode = node as FieldInsnNode
                stack.add(Type.getType(fieldNode.desc).toValueRef())
            }

            PUTFIELD -> {
                val fieldNode = node as FieldInsnNode
                stack.safePop(Type.getType(fieldNode.desc).toValueRef())
                // pop value, then object ref
                stack.safePop(TypeSort.OBJECT)
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
                    stack.safePop(TypeSort.OBJECT) // 'this'
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

                stack.safePop(TypeSort.INT)
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
                stack.safePop(TypeSort.INT)
                stack.add(ObjectValueRef(Type.getObjectType("[" + Type.getObjectType(node.desc).descriptor)))
            }

            ARRAYLENGTH -> {
                // pop array ref, push int
                stack.safePop(TypeSort.OBJECT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            ATHROW -> {
                // pop throwable ref, typical control flow ends
                stack.safePop(TypeSort.OBJECT)
            }

            CHECKCAST -> {
                node as TypeInsnNode
                // pop object ref, push (casted) object ref
                stack.safePop(TypeSort.OBJECT)
                stack.add(ObjectValueRef(Type.getObjectType(node.desc)))
            }

            INSTANCEOF -> {
                // pop object ref, push int (0 or 1)
                stack.safePop(TypeSort.OBJECT)
                stack.add(SortValueRef(TypeSort.INT))
            }

            MONITORENTER, MONITOREXIT -> {
                // pop object ref, no push
                stack.safePop(TypeSort.OBJECT)
            }

            MULTIANEWARRAY -> {
                // pop N dimension ints, push array ref
                // for demonstration, use node.arrayDimensions
                repeat((node as MultiANewArrayInsnNode).dims) {
                    stack.safePop(TypeSort.INT)
                }
                stack.add(ObjectValueRef(Type.getType("[" + node.desc)))
            }

            IFNULL, IFNONNULL -> {
                // pop object ref
                stack.safePop(TypeSort.OBJECT)
            }

            LDC -> {
                val cst = (node as LdcInsnNode).cst
                when (cst) {
                    is Int -> stack.add(SortValueRef(TypeSort.INT))
                    is Float -> stack.add(SortValueRef(TypeSort.FLOAT))
                    is Long -> stack.add(SortValueRef(TypeSort.LONG))
                    is Double -> stack.add(SortValueRef(TypeSort.DOUBLE))
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

internal fun SimulatedFrame.toAsmFrame(): Pair<List<Any>, List<Any>> {
    fun JvmValueRef.toAny(): Any? {
        return when (this) {
            NullValueRef -> NULL
            is ObjectValueRef -> objectType.internalName
            is SortValueRef -> {
                when (sort) {
                    TypeSort.INT -> INTEGER
                    TypeSort.LONG -> LONG
                    TypeSort.FLOAT -> FLOAT
                    TypeSort.DOUBLE -> DOUBLE
                    TypeSort.OBJECT -> null
                }
            }
        }
    }

    val entryRange = locals.keys
        .sorted()
        .let { it.first()..it.last() }

    return Pair(
        stack.mapNotNull { it.toAny() },
        entryRange.map {
            locals[it]?.toAny() ?: TOP
        }
    )
}

private fun Any.toJvmRef(): JvmValueRef? {
    return when (this) {
        is String -> ObjectValueRef(Type.getObjectType(this))
        is Int -> {
            when (this) {
                TOP -> null
                INTEGER -> SortValueRef(TypeSort.INT)
                FLOAT -> SortValueRef(TypeSort.FLOAT)
                DOUBLE -> SortValueRef(TypeSort.DOUBLE)
                LONG -> SortValueRef(TypeSort.LONG)
                NULL -> NullValueRef
                UNINITIALIZED_THIS -> {
                    var type: TypeInsnNode? = null

                    var next = (this@toJvmRef as? LabelNode)?.next

                    while (type == null) {
                        if (next == null) {
                            throw IllegalStateException()
                        }

                        if (next is TypeInsnNode && next.opcode == NEW) {
                            type = next
                        }
                    }

                    ObjectValueRef(Type.getObjectType(type.desc))
                }

                else -> throw IllegalStateException()
            }
        }

        else -> throw IllegalStateException()
    }
}

internal fun FrameNode.toSimulatedFrame(): SimulatedFrame? {
    if (type != F_NEW) throw IllegalArgumentException(
        "Cannot read compressed frames. (Please expand frames on your class reader)"
    )

    return SimulatedFrame(
        stack.mapNotNull { it.toJvmRef() },
        local
            .withIndex()
            .map { it.index to it.value.toJvmRef() }
            .filterNot { it.second == null }
            .toMap() as Map<Int, JvmValueRef>
    )
}

internal fun Type.toJvmType(): TypeSort {
    return when (this.sort) {
        Type.INT, Type.SHORT, Type.BYTE, Type.CHAR, Type.BOOLEAN -> return TypeSort.INT
        Type.FLOAT -> return TypeSort.FLOAT
        Type.LONG -> return TypeSort.LONG
        Type.DOUBLE -> return TypeSort.DOUBLE
        Type.ARRAY, Type.OBJECT -> return TypeSort.OBJECT
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
    //   accurately do this. Note that the AnalyzerAdapter does not do this though
    val areBothObjects = expected is ObjectValueRef && top is ObjectValueRef

    val expectsAnalogousObjectAndIs = expected is SortValueRef &&
            expected.sort == TypeSort.OBJECT &&
            top is ObjectValueRef

    val isNullAndExpectsObject =
        expected is ObjectValueRef || (expected as? SortValueRef)?.sort == TypeSort.OBJECT && top is NullValueRef

    if (top != expected && !areBothObjects && !expectsAnalogousObjectAndIs && !isNullAndExpectsObject) {
        throw IllegalStateException(
            "Expected top of stack to be $expected but found $top."
        )
    }
    return top
}