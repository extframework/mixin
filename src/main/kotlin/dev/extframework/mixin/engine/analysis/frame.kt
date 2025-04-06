package dev.extframework.mixin.engine.analysis

import dev.extframework.mixin.api.TypeSort
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AnalyzerAdapter
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

public fun analyzeFrames(
    target: AbstractInsnNode,
    initial: SimulatedFrame = SimulatedFrame(listOf(), mapOf())
): SimulatedFrame {
    val adapter = AnalyzerAdapter(
        "",
        ACC_PUBLIC,
        "",
        "()V",
        null
    )

    val (stack, locals) = initial.toAsmFrame(adapter)
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
            initialFrame = next.toSimulatedFrame(adapter)
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

    return SimulatedFrame(
        adapter.stack?.mapNotNull { it.toJvmRef(adapter) } ?: listOf(),
        adapter.locals
            ?.withIndex()
            ?.map { it.index to it.value.toJvmRef(adapter) }
            ?.filterNot { it.second == null }
            ?.toMap() as? Map<Int, JvmValueRef> ?: mapOf()
    )
}

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
                analyzerAdapter.visitTableSwitchInsn(
                    node.min,
                    node.max,
                    node.dflt.label,
                    *node.labels.map { it.label }.toTypedArray()
                )
            }

            is LookupSwitchInsnNode -> {
                analyzerAdapter.visitLookupSwitchInsn(
                    node.dflt.label,
                    node.keys.toIntArray(),
                    node.labels.map { it.label }.toTypedArray()
                )
            }

            is MultiANewArrayInsnNode -> {
                analyzerAdapter.visitMultiANewArrayInsn(node.desc, node.dims)
            }
        }
    }
}

internal fun SimulatedFrame.toAsmFrame(
    adapter: AnalyzerAdapter,
): Pair<List<Any>, List<Any>> {
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

            is UninitializedObjectRef -> {
                val label = Label()
                adapter.uninitializedTypes[label] = objectType.internalName
                return label
            }
        }
    }

    val entryRange = locals.keys
        .takeUnless { it.isEmpty() }
        ?.sorted()
        ?.let { it.first()..it.last() }
        ?: listOf()

    return Pair(
        stack.mapNotNull { it.toAny() },
        entryRange.map {
            locals[it]?.toAny() ?: TOP
        }
    )
}

private fun Any.toJvmRef(
    adapter: AnalyzerAdapter,
): JvmValueRef? {
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

                else -> throw IllegalStateException()
            }
        }

        is Label -> {
            val type = adapter.uninitializedTypes[this] as? String ?: return null
            UninitializedObjectRef(
                Type.getObjectType(type),
                this
            )
        }

        else -> throw IllegalStateException()
    }
}

internal fun FrameNode.toSimulatedFrame(
    adapter: AnalyzerAdapter,
): SimulatedFrame? {
    if (type != F_NEW) throw IllegalArgumentException(
        "Cannot read compressed frames. (Please expand frames on your class reader)"
    )

    return SimulatedFrame(
        stack.mapNotNull { it.toJvmRef(adapter) },
        local
            .withIndex()
            .map { it.index to it.value.toJvmRef(adapter) }
            .filterNot { it.second == null }
            .toMap() as Map<Int, JvmValueRef>
    )
}