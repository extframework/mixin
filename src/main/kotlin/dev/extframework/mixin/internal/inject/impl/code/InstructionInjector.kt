package dev.extframework.mixin.internal.inject.impl.code

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.MixinExceptionCause
import dev.extframework.mixin.api.*
import dev.extframework.mixin.internal.analysis.JvmValueRef
import dev.extframework.mixin.internal.analysis.ObjectValueRef
import dev.extframework.mixin.internal.analysis.SimulatedFrame
import dev.extframework.mixin.internal.analysis.analyzeFrames
import dev.extframework.mixin.internal.analysis.toValueRef
import dev.extframework.mixin.annotation.AnnotationTarget
import dev.extframework.mixin.internal.inject.ConflictingMixinInjections
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import dev.extframework.mixin.internal.inject.MixinInjector.InjectionHelper
import dev.extframework.mixin.internal.inject.impl.method.MethodInjector
import dev.extframework.mixin.internal.util.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.*

public class InstructionInjector(
    private val methodInjector: MethodInjector,
    private val customPointProvider: (name: String) -> Class<out InstructionSelector>
) : MixinInjector<InstructionInjectionData> {
    private val customInjectionPoints = HashMap<Pair<String, List<String>>, InstructionSelector>()

    // -----------------------------------
    // +++++++ Annotation Parsing ++++++++
    // -----------------------------------
    override fun parse(
        target: AnnotationTarget,
        annotation: AnnotationNode,
    ): InstructionInjectionData {
        val annotation = createAnnotation(annotation)

        val singlePoint = annotation.point != null
        val blockPoint = annotation.block != null

        val mixinClassRef: ClassReference = target.classNode.ref()

        val point = if (singlePoint && blockPoint) {
            throw InvalidMixinException(
                mixinClassRef,
                MixinExceptionCause.DoubleCodeInjectionPoints,
                listOf(target.methodNode.method().toString())
            )
        } else if (singlePoint) {
            SingleInjectionPoint(
                newSelector(annotation.point) { mixinClassRef },
                annotation.ordinal,
                annotation.count,
            )
        } else if (blockPoint) {
            if (annotation.block.size != 2) {
                throw InvalidMixinException(
                    mixinClassRef,
                    MixinExceptionCause.InvalidCodeInjectionPointBlockSize
                )
            }

            BlockInjectionPoint(
                (newSelector(annotation.block[0]) { mixinClassRef }),
                (newSelector(annotation.block[1]) { mixinClassRef }),
                annotation.ordinal,
                annotation.count,
            )
        } else {
            SingleInjectionPoint(
                newSelector(SelectData(InjectionBoundary.HEAD)) { mixinClassRef },
                annotation.ordinal,
                annotation.count,
            )

//            throw InvalidMixinException(
//                mixinClassRef,
//                MixinExceptionCause.NoCodeInjectionPoints,
//                listOf(target.methodNode.method().toString()),
//            )
        }

        // TODO not mutating the mixin node (we already do this, possible do a deep copy?)

        return InstructionInjectionData(
            mixinClassRef,
            target.methodNode,
            inferTargetMethod(target.methodNode, annotation.method),
            annotation.type,
            point,
            annotation.locals
        )
    }

    // ------------------------------------
    // ++++++++++++ Injecting +++++++++++++
    // ------------------------------------
    override fun inject(
        node: ClassNode,
        helper: InjectionHelper<InstructionInjectionData>,
    ) {
        // Only transforms instructions anyways, residuals are where issues may occur
        // but the method injector will handle that :)
        fullyRedefiningInjection(node, helper.applicable())

        helper.inject(
            node,
            methodInjector,
            helper.applicable().map {
                MethodInjector.buildData(
                    it.mixinMethod
                )
            }
        )
    }

//    override fun residualsFor(
//        data: InstructionInjectionData,
//        applicator: MixinApplicator
//    ): List<MixinInjector.Residual<*>> {
//        return listOf(
//            MixinInjector.Residual(
//                MethodInjector.buildData(data.mixinMethod),
//                applicator,
//                methodInjector
//            )
//        )
//    }

    // -------------------------------------
    // +++++++++++++ Internal ++++++++++++++
    // -------------------------------------

    internal fun fullyRedefiningInjection(
        node: ClassNode,
        all: List<InstructionInjectionData>
    ) {
        all.groupBy {
            findTargetMethod(
                it.inferredTarget,
                node.methods.map { it.method() },
            ) { it.mixinClass to node.ref() }
        }.forEach { (method, toInject) ->
            val methodNode = node.methods.first { it.method() == method }
            val methodDescriptor = methodNode.method()
            val isStatic = methodNode.access.and(ACC_STATIC) == ACC_STATIC

//            val totalFrame = analyzeFrames(
//                methodNode.instructions.last,
//                SimulatedFrame(
//                    listOf(),
//                    (if (isStatic) listOf() else listOf(
//                        ObjectValueRef(Type.getObjectType(node.name))
//                    )) + methodDescriptor.argumentTypes.map { it.toValueRef() }
//                )
//            )

            val locals = LocalTracker(
//                totalFrame.locals.keys.maxOrNull() ?: 0
                methodNode.maxLocals
            )

            val placedInjections: List<Pair<InstructionInjectionData, InjectionPoint.Group>> = toInject.flatMap {
                it.point.getPoints(methodNode.instructions)
                    .map { insn -> it to insn }
            }

            val placements: Map<InjectionPoint.Group, List<InstructionInjectionData>> =
                placedInjections.groupBy { it.second }
                    .mapValues { (_, value) -> value.map { it.first } }

            val conflicting = placements.values.find { it ->
                it.any { it.injectionType == InjectionType.OVERWRITE } && it.size > 1
            }

            if (conflicting != null) {
                throw ConflictingMixinInjections(
                    conflicting.map {
                        Triple(it.mixinClass, it.mixinMethod.method(), it.injectionType)
                    }
                )
            }

            toInject.forEach { it: InstructionInjectionData ->
                val isMixinMethodStatic = it.mixinMethod.access.and(ACC_STATIC) == ACC_STATIC

                if (isStatic != isMixinMethodStatic) {
                    throw CodeInjectionException(
                        "Mixin: '${it.mixinClass}' contains code injection: '${it.mixinMethod.method()}' which should ${
                            if (isStatic) "" else "not "
                        }be static."
                    )
                }
            }

            data class BuiltFlow(
                val type: InjectionType,
                val target: InsnList,
                val group: InjectionPoint.Group,
                val flow: InsnList
            )

            placedInjections
                .groupBy { (data, node) -> node to data.injectionType }
                .mapValues { (_, value) -> value.map { it.first } }
                .map { (point: Pair<InjectionPoint.Group, InjectionType>, toInject: List<InstructionInjectionData>) ->
                    val (group, type) = point

                    val targetInstructions = methodNode.instructions

                    val frame = analyzeFrames(
                        when (type) {
                            InjectionType.BEFORE -> group.start
                            InjectionType.AFTER -> group.end.next
                            // The values will get overwritten so we cant put it at the end
                            InjectionType.OVERWRITE -> group.start
                        },
                        SimulatedFrame(
                            listOf(),
                            (if (isStatic) listOf() else listOf(
                                ObjectValueRef(Type.getObjectType(node.name))
                            )) + methodDescriptor.argumentTypes.map { it.toValueRef() }
                        )
                    )

                    val flowInsn = buildMixinFlow(
                        toInject,
                        frame,
                        locals,
                        node.ref(),
                        isStatic,
                        methodDescriptor.returnType
                            ?.takeUnless { it == Type.VOID_TYPE }
                            ?.toValueRef()
                    )

                    BuiltFlow(
                        type,
                        targetInstructions,
                        group,
                        flowInsn
                    )
                }.forEach { (type, targetInsn, group, flowInsn) ->
                    when (type) {
                        InjectionType.BEFORE -> {
                            targetInsn.insertBefore(group.start, flowInsn)
                        }

                        InjectionType.AFTER -> {
                            targetInsn.insert(group.end, flowInsn)
                        }

                        InjectionType.OVERWRITE -> {
                            targetInsn.insert(group.end, flowInsn)

                            removeAll(group, targetInsn)
                        }
                    }
                }

            methodNode.maxLocals = locals.current + 1
        }
    }

    private fun removeAll(
        group: InjectionPoint.Group,
        instructions: InsnList
    ) {
        var next: AbstractInsnNode? = group.start
        // Use next.previous to get one beyond the current
        while (next != null && next.previous != group.end) {
            val curr = next
            next = curr.next

            instructions.remove(curr)
        }
    }

    internal fun captureStack(
        stack: List<JvmValueRef>,
        localTracker: LocalTracker,
        insn: InsnList,
    ) {
        val captured = stack.reversed().map { type ->
            val varIndex = localTracker.increment(
                type.sort
            )
            insn.add(VarInsnNode(ISTORE + type.sort.offset, varIndex))
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
        for ((i, type) in stack.withIndex()) {
            insn.add(VarInsnNode(ALOAD, stackSlot))

            insn.add(IntInsnNode(BIPUSH, i))
            insn.add(
                MethodInsnNode(
                    INVOKEINTERFACE, Stack::class.internalName, "get", "(I)${Any::class.descriptor}", true
                )
            )
            manualUnbox(type, insn)
        }
    }

    internal fun instantiateFlowObj(
        insn: InsnList
    ) {
        insn.add(TypeInsnNode(NEW, InternalMixinFlow::class.internalName))
        insn.add(InsnNode(DUP))
        insn.add(MethodInsnNode(INVOKESPECIAL, InternalMixinFlow::class.internalName, "<init>", "()V", false))
    }

    private fun determineParameters(
        method: Method,
        flowSlot: Int,
        stackSlot: Int?,
        localSlots: List<Int>,

        err: () -> ClassReference
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

                else -> throw CodeInjectionException(
                    "Mixin: '${err()}' contains code injection: '${method}' which has " +
                            "parameter: '$it'. All parameter types must either be a 'MixinFlow', " +
                            "'Captured' type, or 'Stack' type."
                )
            }
        }
    }

    private fun hasFlowResult(
        method: Method,
    ): Boolean {
        return method.returnType == Type.getType(MixinFlow.Result::class.java)
    }

    private fun callInjection(
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

    internal fun buildMixinFlow(
        all: List<InstructionInjectionData>,

        frame: SimulatedFrame,
        locals: LocalTracker,

        targetClass: ClassReference,
        isStatic: Boolean,
        returnType: JvmValueRef?,
    ): InsnList {
        val instructions = InsnList()

        // Check if any injection needs the stack captured
        val capturesStack = all.any {
            it.mixinMethod.method().argumentTypes.any {
                it == Type.getType(Stack::class.java)
            }
        }

        val sortedLocals = frame.locals
            .entries
            .sortedBy { it.key }

        fun findLocalSlot(slot: Int): Int {
            return sortedLocals[
                slot + if (isStatic) 0 else 1
            ].key
        }

        // Check which locals need to be captured
        val requiredLocals = all.flatMapTo(HashSet()) {
            it.capturedLocals
        }.sorted().map(::findLocalSlot)

        // Check that this injection is valid
        for (data in all) {
            checkValidity(
                data,
                data.capturedLocals.map(::findLocalSlot),
                frame
            )
        }

        val capturedStack = if (!isStatic) {
            frame.stack.toMutableList()
                .apply {
                    removeFirst()
                }
        } else frame.stack

        // Stack slot, this won't be a valid value if capturedStack is not true
        val stackObjSlot = if (capturesStack) {
            // Increment slot and capture
            val slot = locals.increment(
                TypeSort.OBJECT
            )

            instructions.add(LabelNode(Label()))

            // Capture the stack and store the resulting value as a local.
            captureStack(
                capturedStack,
                locals,
                instructions
            )
            instructions.add(VarInsnNode(ASTORE, slot))

            slot
        } else -1

        // Similar to before, increment used locals, add label, capture local, and store.
        val capturedLocalSlots = requiredLocals.map { original ->
            val localType = frame.locals[original]!!

            val slot = locals.increment(TypeSort.OBJECT)

            instructions.add(LabelNode(Label()))

            captureLocal(original, localType, instructions)

            instructions.add(VarInsnNode(ASTORE, slot))

            slot to original
        }

        // Instantiate the mixin flow object (label, new, and store)
        val mixinFlowObjSlot = locals.increment(TypeSort.OBJECT)
        instructions.add(LabelNode(Label()))
        instantiateFlowObj(instructions)
        instructions.add(VarInsnNode(ASTORE, mixinFlowObjSlot))

        // Accumulator
        val accumulatorSlot = locals.increment(TypeSort.OBJECT)

        instructions.add(LabelNode(Label()))
        instructions.add(TypeInsnNode(NEW, MixinFlow.Result::class.internalName))

        // An accumulator that yields. (Equivalent to new MixinFlow.Result(false, null, null))
        instructions.add(InsnNode(DUP))
        instructions.add(InsnNode(ICONST_0))
        instructions.add(InsnNode(ACONST_NULL))
        instructions.add(InsnNode(ACONST_NULL))
        instructions.add(
            MethodInsnNode(
                INVOKESPECIAL, MixinFlow.Result::class.internalName, "<init>", "(Z${
                    Any::class.descriptor
                }${TypeSort::class.descriptor})V", false
            )
        )
        instructions.add(VarInsnNode(ASTORE, accumulatorSlot))

        // At this point the accumulator result (which is just a 'continue') is stored
        // in the local variables. Each injection call should not modify the stack
        // unless it adds another result on top of it. In that case we fold them
        // together and store the new accumulator.

        for (data in all) {
            val parameters = determineParameters(
                data.mixinMethod.method(),
                mixinFlowObjSlot,
                stackObjSlot,
                capturedLocalSlots.map { it.first }
            ) {
                data.mixinClass
            }

            instructions.add(LabelNode(Label()))
            // If this mixin has a flow result then we want to load the accumulator
            // flow and fold, if not then those steps can be ignored.
            val hasResult = hasFlowResult(data.mixinMethod.method())

            if (hasResult) instructions.add(VarInsnNode(ALOAD, accumulatorSlot))

            // Folding if the stack is updated.
            callInjection(targetClass, data.mixinMethod.method(), isStatic, parameters, instructions)

            if (hasResult) {
                // Fold
                instructions.add(
                    MethodInsnNode(
                        INVOKEVIRTUAL, MixinFlow.Result::class.internalName, "fold", "(${
                            MixinFlow.Result::class.descriptor
                        })${MixinFlow.Result::class.descriptor}"
                    )
                )

                // Store
                instructions.add(VarInsnNode(ASTORE, accumulatorSlot))
            }
        }

        val labelContinue = LabelNode(Label())

        // Load the accumulator, dup it, and see if it yields (wants a return)
        instructions.add(VarInsnNode(ALOAD, accumulatorSlot))
        instructions.add(InsnNode(DUP))
        instructions.add(
            FieldInsnNode(
                GETFIELD,
                MixinFlow.Result::class.internalName,
                "yielding",
                Type.BOOLEAN_TYPE.descriptor
            )
        )
        // If it is not a return, jump to our 'flow on' (continue) label.
        instructions.add(JumpInsnNode(IFEQ, labelContinue))

        fun getResultValue() {
            instructions.add(
                FieldInsnNode(
                    GETFIELD,
                    MixinFlow.Result::class.internalName,
                    "value",
                    Any::class.descriptor
                )
            )
        }

        // Based on the return type of the method (maybe box) and return.
        instructions.add(LabelNode(Label()))
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
                instructions.add(InsnNode(POP))
                instructions.add(InsnNode(RETURN))
            }
        }

        instructions.add(labelContinue)

        instructions.add(InsnNode(POP)) // Pop the extra 'result' off the top of the stack

        capturedLocalSlots.forEach { (objSlot, original) ->
            instructions.add(LabelNode(Label()))
            releaseLocal(objSlot, original, frame.locals[original]!!, instructions)
        }

        if (capturesStack) {
            instructions.add(LabelNode(Label()))
            releaseStack(
                stackObjSlot,
                capturedStack,
                instructions
            )
        }

        return instructions
    }

    private fun checkValidity(
        data: InstructionInjectionData,
        locals: List<Int>,
        frame: SimulatedFrame,
    ) {
        val flowResultType = Type.getType(MixinFlow.Result::class.java)

        // Check that no local exceeds the max local size
        val maxRequestedLocals = locals.maxOrNull() ?: 0
        if (maxRequestedLocals > (frame.locals.keys.maxOrNull() ?: 0)) {
            throw CodeInjectionException(
                "Mixin: '${data.mixinClass}' contains code injection: '${data.mixinMethod.method()}' which requests " +
                        "more locals than are present by its point of injection. The available locals are: '${frame.locals}'. " +
                        "This mixin requests a local of slot: '$maxRequestedLocals'."
            )
        }

        // Check that the mixin returns either void or MixinFlow.Result
        val returnType = data.mixinMethod.method().returnType

        if (returnType != Type.VOID_TYPE && returnType != flowResultType) {
            throw CodeInjectionException(
                "Mixin: '${data.mixinClass}' contains code injection: '${data.mixinMethod.method()}' and " +
                        "specifies a return type of '${returnType}'. The return type for a code " +
                        "injection method should either be 'void' or 'MixinFlow.Result'."
            )
        }

        // Local checks
        val signature = data.mixinMethod.signature
        if (signature != null) {
            // Infer locals
            val inferredLocals = inferLocalsFromSignature(
                signature
            )

            // Check that the mixin defines as many 'Captured's as it requests in the annotation.
            if (inferredLocals.size != locals.size) {
                throw CodeInjectionException(
                    "Mixin: '${data.mixinClass}' contains code injection: '${data.mixinMethod.method()}' which defines a different " +
                            "amount of locals to capture in its @InjectCode annotation than it does in its method signature. Expected to" +
                            "find '${locals.size}' parameters matching: 'Captured<...>' but found '${inferredLocals.size}'"
                )
            }

            locals
                .zip(inferredLocals)
                .map { (slot, inferred) ->
                    IndexedValue(slot, frame.locals[slot]!! to inferred.toValueRef())
                }
                .forEach { (slot, it) ->
                    val (inferred: JvmValueRef, actual: JvmValueRef) = it
                    if (!inferred.boxableTo(actual)) {
                        throw CodeInjectionException(
                            "Mixin: '${data.mixinClass}' contains code injection: '${data.mixinMethod.method()}' which requests to capture a local" +
                                    "variable in slot: '$slot' (if your mixin is non static, you've probably defined it as slot '${slot - 1}') with type: " +
                                    "'$inferred' but the actual type in local slot '$slot' is '$actual'."
                        )
                    }
                }
        }
    }

    private fun createAnnotation(
        inject: AnnotationNode,
    ): InjectCodeData {
        fun parseSelection(
            select: AnnotationNode
        ) = SelectData(
            select.enumValue<InjectionBoundary>("value") ?: InjectionBoundary.IGNORE,
            select.value<AnnotationNode>("invoke")?.let { invoke ->
                InvokeData(
                    invoke.value("value") ?: (Type.getObjectType(invoke.value<String>("clsName")!!.replace(".", "/"))),
                    invoke.value("method")!!,
                    invoke.value("opcode"),
                )
            },
            select.value<AnnotationNode>("field")?.let { field ->
                FieldData(
                    field.value("value")!!,
                    field.value("name")!!,
                    field.enumValue<FieldAccessType>("type") ?: FieldAccessType.EITHER,
                )
            },
            select.value<AnnotationNode>("opcode")?.let { opcode ->
                OpcodeData(
                    opcode.value<Int>("value")?.takeUnless { it == -1 },
                    opcode.value<AnnotationNode>("ldc")?.value<String>("value")
                )
            },
            select.value<AnnotationNode>("custom")?.let { custom ->
                CustomData(
                    custom.value<Type>("value")!!,
                    custom.value<Array<String>>("options")?.toList() ?: emptyList(),
                )
            },
        )

        return InjectCodeData(
            inject.value("value") ?: "",
            inject.enumValue<InjectionType>(
                "type"
            ) ?: InjectionType.BEFORE,
            inject.value<AnnotationNode>("point")?.let(::parseSelection),
            inject.value<Array<AnnotationNode>>("block")?.map(::parseSelection),
            inject.value<List<Int>>("locals") ?: emptyList(),
            inject.value("ordinal") ?: 0,
            inject.value("count") ?: 1
        )
    }

    private fun newSelector(
        select: SelectData,
        target: () -> ClassReference
    ): InstructionSelector {
        val boundary = select.boundary != InjectionBoundary.IGNORE
        val invoke = select.invoke != null
        val field = select.field != null
        val opcode = select.opcode != null
        val custom = select.custom != null

        var notExclusive = false // Starts exclusive
        for (bool in listOf(boundary, invoke, field, opcode, custom)) {
            if (notExclusive && bool) {
                throw InvalidMixinException(
                    target(),
                    MixinExceptionCause.CodeSelectorExclusivity
                )
            }

            notExclusive = notExclusive || bool
        }

        return if (boundary) {
            BoundarySelector(select.boundary)
        } else if (invoke) {
            InvocationSelector(
                select.invoke.cls,
                methodFromDesc(
                    // Normalizing return type
                    select.invoke.method.substringBeforeLast(")") + ")V"
                ),
                select.invoke.opcode.takeUnless { it == -1 },
            )
        } else if (field) {
            FieldAccessSelector(
                select.field.cls,
                select.field.name,
                select.field.type
            )
        } else if (opcode) {
            OpcodeSelector(
                select.opcode.opcode ?: Opcodes.LDC,
                select.opcode.ldc
            )
        } else if (custom) {
            val key = select.custom.cls.internalName to select.custom.options

            customInjectionPoints[key] ?: run {
                val cls = customPointProvider(select.custom.cls.className)
                val instance: InstructionSelector =
                    cls.getConstructor(List::class.java).newInstance(select.custom.options)!!

                customInjectionPoints[key] = instance

                instance
            }
        } else {
            throw InvalidMixinException(
                target(),
                MixinExceptionCause.NoCodeSelectorsDefined,
            )
        }
    }

    private fun inferTargetMethod(
        mixinMethod: MethodNode,
        // Could be empty, an internal type ('name(<internal type params>)') or a fancy type ('name (<classnames>)')
        given: String,
    ): Method {
        val name = given
            .substringBefore("(")
            .substringBefore(" ")
            .takeUnless { it.isBlank() }
            ?: mixinMethod.name

        if (given.contains(" ")) {
            val curr = if (given.count { ch -> ch == ' ' } == 2) {
                given.substringAfter(" ")
            } else given

            return Method.getMethod("void $name ${curr.substringAfter(" ")}")
        }

        val descriptor = if (given.contains("(")) {
            "(" + given.substringAfter("(")
        } else
        // FIXME: This will also incldue locals, not just parameters.
            inferLocalsFromSignature(mixinMethod.signature).joinToString(
                prefix = "(",
                separator = "",
                postfix = ")V"
            )

        return Method(name, descriptor.substringBefore(")") + ")V")
    }

    public companion object {
        public fun findTargetMethod(
            inferred: Method,
            options: List<Method>,

            ifErr: () -> Pair<ClassReference, ClassReference>
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

    }

    private data class InjectCodeData(
        val method: String,
        val type: InjectionType,
        val point: SelectData?,
        val block: List<SelectData>?,
        val locals: List<Int>,

        val ordinal: Int,
        val count: Int,
    )

    private data class SelectData(
        val boundary: InjectionBoundary = InjectionBoundary.IGNORE,
        val invoke: InvokeData? = null,
        val field: FieldData? = null,
        val opcode: OpcodeData? = null,
        val custom: CustomData? = null,
    )

    private data class CustomData(
        val cls: Type,
        val options: List<String>
    )

    private data class OpcodeData(
        val opcode: Int?,
        val ldc: String?,
    )

    private data class FieldData(
        val cls: Type,
        val name: String,
        val type: FieldAccessType
    )

    private data class InvokeData(
        val cls: Type,
        val method: String,
        val opcode: Int?
    )
}

public data class InstructionInjectionData(
    val mixinClass: ClassReference,

    val mixinMethod: MethodNode,
    val inferredTarget: Method,

    val injectionType: InjectionType,
    val point: InjectionPoint,

    val capturedLocals: List<Int>,
) : InjectionData