package dev.extframework.mixin.engine.impl.code

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.MixinExceptionCause
import dev.extframework.mixin.api.*
import dev.extframework.mixin.engine.analysis.JvmValueRef
import dev.extframework.mixin.engine.analysis.SimulatedFrame
import dev.extframework.mixin.engine.analysis.UninitializedObjectRef
import dev.extframework.mixin.engine.analysis.analyzeFrames
import dev.extframework.mixin.engine.tag.ClassTag
import dev.extframework.mixin.engine.util.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.*


internal fun removeAll(
    start: AbstractInsnNode,
    end: AbstractInsnNode,
    instructions: InsnList
) {
    var next: AbstractInsnNode? = start
    // Use next.previous to get one beyond the current
    while (next != null && next.previous != end) {
        val curr = next
        next = curr.next

        instructions.remove(curr)
    }
}

private fun newUninitializedTypeWrapper(
    insn: InsnList,
    type: Type
) {
    insn.add(TypeInsnNode(NEW, UninitializedType::class.internalName))
    insn.add(InsnNode(DUP))

    insn.add(TypeInsnNode(NEW, ClassReference::class.internalName))
    insn.add(InsnNode(DUP))

    insn.add(LdcInsnNode(type.internalName))

    insn.add(MethodInsnNode(
        INVOKESPECIAL,
        ClassReference::class.internalName,
        "<init>",
        "(${String::class.descriptor})V",
        false
    ))

    insn.add(MethodInsnNode(
        INVOKESPECIAL,
        UninitializedType::class.internalName,
        "<init>",
        "(${ClassReference::class.descriptor})V",
        false
    ))
}

internal fun captureStack(
    stack: List<JvmValueRef>,
    localTracker: LocalTracker,
    insn: InsnList,
) {
    val captured = stack.reversed().map { type ->
       val varIndex = when (type) {
            is UninitializedObjectRef -> {
                val varIndex = localTracker.increment(
                    TypeSort.OBJECT
                )

                insn.add(InsnNode(POP))
                newUninitializedTypeWrapper(insn, type.objectType)

                insn.add(VarInsnNode(ASTORE, varIndex))

                varIndex
            }
            else -> {
                val varIndex = localTracker.increment(
                    type.sort
                )

                insn.add(VarInsnNode(ISTORE + type.sort.offset, varIndex))

                varIndex
            }
        }


        varIndex to type
    }

    // Create the stack type. Adding it to the stack to be used later
    insn.add(TypeInsnNode(NEW, InternalMixinStack::class.internalName))
    insn.add(InsnNode(DUP))

    // Instantiate sized array
    insn.add(IntInsnNode(BIPUSH, stack.size))
    insn.add(TypeInsnNode(ANEWARRAY, Any::class.internalName))

    // Add items to stack to array
    for ((i, pair) in captured.withIndex()) {
        val (slot, type) = pair

        // Dup the array object
        insn.add(InsnNode(DUP))
        // The index, we do this so it reverses
        insn.add(IntInsnNode(BIPUSH, captured.size - i - 1))

        // Load and box the type
        manualBox(type.sort, slot, insn)

        // Store onto array
        insn.add(InsnNode(AASTORE))
    }

    insn.add(
        MethodInsnNode(
            INVOKESPECIAL,
            InternalMixinStack::class.internalName,
            "<init>",
            "([${Any::class.descriptor})V",
            false
        )
    )
}

internal fun captureLocal(
    originalSlot: Int,
    type: JvmValueRef,
    insn: InsnList,
) {
    insn.add(TypeInsnNode(NEW, InternalCaptured::class.internalName))
    insn.add(InsnNode(DUP))

    manualBox(type.sort, originalSlot, insn)
    insn.add(FieldInsnNode(GETSTATIC, TypeSort::class.internalName, type.sort.name, TypeSort::class.descriptor))

    insn.add(
        MethodInsnNode(
            INVOKESPECIAL, InternalCaptured::class.internalName, "<init>", "(${
                Any::class.descriptor
            }${TypeSort::class.descriptor})V", false
        )
    )
}

internal fun releaseLocal(
    capturedObjSlot: Int,
    originalSlot: Int,
    type: JvmValueRef,
    insn: InsnList
) {
    insn.add(VarInsnNode(ALOAD, capturedObjSlot))
    insn.add(TypeInsnNode(CHECKCAST, Captured::class.internalName))
    insn.add(
        MethodInsnNode(
            INVOKEINTERFACE,
            Captured::class.internalName,
            "get",
            "()${Any::class.descriptor}",
            true
        )
    )
    manualUnbox(type, insn)
    insn.add(VarInsnNode(ISTORE + type.sort.offset, originalSlot))
}

internal fun releaseStack(
    stackSlot: Int,
    stack: List<JvmValueRef>,
    insn: InsnList,
) {
    val previouslyInstantiated = HashSet<Label>()

    for ((i, type) in stack.withIndex()) {
        insn.add(VarInsnNode(ALOAD, stackSlot))

        insn.add(IntInsnNode(BIPUSH, i))
        insn.add(
            MethodInsnNode(
                INVOKEINTERFACE, Stack::class.internalName, "get", "(I)${Any::class.descriptor}", true
            )
        )

        when (type) {
            is UninitializedObjectRef -> {
                insn.add(InsnNode(POP))

                if (previouslyInstantiated.add(type.label)) {
                    insn.add(TypeInsnNode(NEW, type.objectType.internalName))
                } else {
                    // Assuming its on the top of the stack, if it's not, there's nothing we can do.
                    insn.add(InsnNode(DUP))
                }
            }
            else -> {
                manualUnbox(type, insn)
            }
        }

    }
}

internal fun instantiateFlowObj(
    insn: InsnList
) {
    insn.add(TypeInsnNode(NEW, InternalMixinFlow::class.internalName))
    insn.add(InsnNode(DUP))
    insn.add(MethodInsnNode(INVOKESPECIAL, InternalMixinFlow::class.internalName, "<init>", "()V", false))
}

internal fun determineParameters(
    method: Method,
    flowSlot: Int,
    stackSlot: Int?,
    localSlots: List<Int>,
): List<Int> {
    var localIndex = 0
    return method.argumentTypes.map {
        when (it) {
            Type.getType(MixinFlow::class.java) -> flowSlot
            Type.getType(Stack::class.java) -> stackSlot ?: throw Exception()
            Type.getType(Captured::class.java) -> {
                val slot = localSlots[localIndex]
                localIndex++
                slot
            }

            else -> throw Exception("This should not happen.")
        }
    }
}

internal fun hasFlowResult(
    method: Method,
): Boolean {
    return method.returnType == Type.getType(MixinFlow.Result::class.java)
}

internal fun callInjection(
    cls: ClassReference,
    method: Method,
    isStatic: Boolean,

    argSlots: List<Int>,

    insn: InsnList
) {
    if (!isStatic) {
        insn.add(VarInsnNode(ALOAD, 0))
    }

    argSlots.forEach { t ->
        insn.add(VarInsnNode(ALOAD, t))
    }

    insn.add(
        MethodInsnNode(
            if (!isStatic) INVOKEVIRTUAL else INVOKESTATIC,

            cls.internalName, method.name, method.descriptor, false
        )
    )
}

public fun findTargetMethod(
    inferred: Method,
    options: List<Method>,

    ifErr: () -> Pair<ClassTag, ClassReference>
): Method {
    val method = inferred

    val overloading = options
        .filter { it.name == method.name }

    if (overloading.isEmpty()) {
        throw InvalidMixinException(
            ifErr().first,
            MixinExceptionCause.NoCodeMethodFound,
            listOf(method.name, ifErr().second.toString())
        )
    } else if (overloading.size == 1) {
        return overloading[0]
    } else if (overloading.any { it == inferred }) {
        return inferred
    } else {
        val matchingMethods = method.argumentTypes
            .withIndex()
            .fold(overloading) { acc, arg ->
                acc.filter {
                    it.argumentTypes.getOrNull(arg.index) == arg.value
                }
            }

        if (matchingMethods.size == 1) {
            return matchingMethods[0]
        } else if (matchingMethods.size > 1) {
            throw InvalidMixinException(
                ifErr().first,
                MixinExceptionCause.CodeMethodOverloadAmbiguity,
                listOf(method.name, matchingMethods.toString())
            )
        } else throw InvalidMixinException(
            ifErr().first,
            MixinExceptionCause.NoCodeMethodFound,
            listOf(method.toString(), ifErr().second.toString())
        )
    }
}

internal fun inferLocalsFromSignature(
    signature: String?
): List<Type> {
    if (signature == null) return listOf()

    val reader = SignatureReader(signature)
    val visitor = object : SignatureVisitor(ASM5) {
        // States:
        //  0 - nothing
        //  parameter - 1
        //  captured type - 2
        var state = 0
        val types = ArrayList<Type>()

        override fun visitParameterType(): SignatureVisitor? {
            state = 1
            return super.visitParameterType()
        }

        override fun visitClassType(name: String) {
            when (state) {
                1 -> {
                    if (name == Captured::class.internalName)
                        state = 2
                }

                2 -> {
                    types.add(Type.getObjectType(name))
                    state = 0
                }
            }
            super.visitClassType(name)
        }
    }

    reader.accept(visitor)

    return visitor.types
}

// This relies on the MixinFlow.Result being on the top of the stack,
// this should be changed to a local variable.
internal fun doReturn(
    returnType: JvmValueRef?,
    instructions: InsnList,
    resultSlot: Int,
) {
    fun getResultValue() {
        instructions.add(VarInsnNode(ALOAD, resultSlot))
        instructions.add(
            FieldInsnNode(
                GETFIELD,
                MixinFlow.Result::class.internalName,
                "value",
                Any::class.descriptor
            )
        )
    }

    when (returnType?.sort) {
        TypeSort.INT -> {
            getResultValue()
            manualUnbox(returnType, instructions)
            instructions.add(InsnNode(IRETURN))
        }

        TypeSort.LONG -> {
            getResultValue()
            manualUnbox(returnType, instructions)
            instructions.add(InsnNode(LRETURN))
        }

        TypeSort.FLOAT -> {
            getResultValue()
            manualUnbox(returnType, instructions)
            instructions.add(InsnNode(FRETURN))
        }

        TypeSort.DOUBLE -> {
            getResultValue()
            manualUnbox(returnType, instructions)
            instructions.add(InsnNode(DRETURN))
        }

        TypeSort.OBJECT -> {
            getResultValue()
            manualUnbox(returnType, instructions)
            instructions.add(InsnNode(ARETURN))
        }

        // If the method returns null, empty the stack and return.
        null -> {
            instructions.add(InsnNode(RETURN))
        }
    }
}

internal fun getConstructorInitializationInstruction(
    instructions: InsnList,
    cls: ClassNode
) : AbstractInsnNode? {
    return instructions
        .asSequence()
        .filterIsInstance<MethodInsnNode>()
        .filter { node -> node.opcode == INVOKESPECIAL }
        .filter { node -> node.owner == (cls.superName ?: Any::class.internalName) }
        .find {
            // The pattern we search for is the first call to the direct super type
            // who's 'this' value is an uninitialized copy of this.
            val frame = analyzeFrames(
                it,
                // We just care about the stack here
                SimulatedFrame(listOf(), listOf(
                    UninitializedObjectRef(
                        Type.getObjectType(cls.name),
                        Label()
                    )
                ))
            )

            val method = Method(it.name, it.desc)

            val referenceType = frame.stack[frame.stack.size - method.argumentTypes.size - 1]

            referenceType is UninitializedObjectRef && referenceType.objectType.internalName == cls.name
        }
}