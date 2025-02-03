package dev.extframework.mixin.internal.inject.impl.code

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.MixinExceptionCause
import dev.extframework.mixin.TargetedApplicator
import dev.extframework.mixin.api.*
import dev.extframework.mixin.internal.analysis.JvmValueRef
import dev.extframework.mixin.internal.analysis.ObjectValueRef
import dev.extframework.mixin.internal.analysis.SimulatedFrame
import dev.extframework.mixin.internal.analysis.analyzeFrames
import dev.extframework.mixin.internal.analysis.toValueRef
import dev.extframework.mixin.internal.annotation.AnnotationTarget
import dev.extframework.mixin.internal.inject.ConflictingMixinInjections
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import dev.extframework.mixin.internal.inject.impl.method.MethodInjectionData
import dev.extframework.mixin.internal.inject.impl.method.MethodInjector
import dev.extframework.mixin.internal.util.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
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

        return InstructionInjectionData(
            mixinClassRef,
            target.methodNode,
            annotation.method,
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
        all: List<InstructionInjectionData>
    ): List<MixinInjector.Residual<*>> {
        // Only transforms instructions anyways, residuals are where issues may occur
        // but the method injector will handle that :)
        return fullyRedefiningInjection(node, all)
    }

    // -------------------------------------
    // +++++++++++++ Internal ++++++++++++++
    // -------------------------------------

    internal fun fullyRedefiningInjection(
        node: ClassNode,
        all: List<InstructionInjectionData>
    ): List<MixinInjector.Residual<*>> {
        all.groupBy {
            findDestMethod(
                it.mixinMethod.method(),
                it.requestedMethod,
                node,
            ) { it.mixinClass }
        }.forEach { (method, toInject) ->
            val methodNode = node.methods.first { it.method() == method }
            val methodDescriptor = methodNode.method()
            val isStatic = methodNode.access.and(ACC_STATIC) == ACC_STATIC

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
                        methodNode.tryCatchBlocks ?: listOf(),
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
                        isStatic,
                        methodDescriptor.returnType
                            ?.takeUnless { it == Type.VOID_TYPE }
                            ?.toValueRef(),
                        node.ref()
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
        }

        return all.map {
            MixinInjector.Residual(
                MethodInjectionData(it.mixinMethod),
                TargetedApplicator(ClassReference(node.name)),
                methodInjector
            )
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
        localsCount: Int,
        insn: InsnList,
    ) {
        for ((i, type) in stack.withIndex()) {
            insn.add(VarInsnNode(ISTORE + type.sort.offset, localsCount + i))
        }

        // Create the stack type. Adding it to the stack to be used later
        insn.add(TypeInsnNode(NEW, InternalMixinStack::class.internalName))
        insn.add(InsnNode(DUP))

        // Instantiate sized array
        insn.add(IntInsnNode(BIPUSH, stack.size))
        insn.add(TypeInsnNode(ANEWARRAY, Any::class.internalName))

        // Add items to stack to array list
        for ((i, type) in stack.withIndex()) {
            // Dup the array object
            insn.add(InsnNode(DUP))
            // The index, we do this so it reverses
            insn.add(IntInsnNode(BIPUSH, stack.size - i - 1))

            // Load and box the type
            manualBox(type.sort, i + localsCount, insn)

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
        local: Int,
        type: JvmValueRef,
        insn: InsnList,
    ) {
        insn.add(TypeInsnNode(NEW, InternalCaptured::class.internalName))
        insn.add(InsnNode(DUP))

        manualBox(type.sort, local, insn)
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
        local: Int,
        type: JvmValueRef,
        insn: InsnList
    ) {
        insn.add(VarInsnNode(ALOAD, local))
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
        manuelUnbox(type, insn)
        insn.add(VarInsnNode(ISTORE + type.sort.offset, local))
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
            manuelUnbox(type, insn)
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

    private fun callInjection(
        cls: ClassReference,
        method: Method,
        isStatic: Boolean,

        argSlots: List<Int>,

        insn: InsnList
    ): Boolean {
        if (!isStatic) {
            insn.add(VarInsnNode(ALOAD, 0))
        }

        argSlots.forEach { t ->
            insn.add(VarInsnNode(ALOAD, t))
        }

        insn.add(MethodInsnNode(INVOKEVIRTUAL, cls.internalName, method.name, method.descriptor, false))

        return method.returnType == Type.getType(MixinFlow.Result::class.java)
    }

    internal fun buildMixinFlow(
        all: List<InstructionInjectionData>,
        frame: SimulatedFrame,
        isStatic: Boolean,
        returnType: JvmValueRef?,
        targetClass: ClassReference,
    ): InsnList {
        val instructions = InsnList()

        val capturedStack = all.any {
            it.mixinMethod.method().argumentTypes.any {
                it == Type.getType(Stack::class.java)
            }
        }

        val requiredLocals = all.flatMapTo(HashSet()) {
            it.capturedLocals
        }.sorted()

        checkConditions(isStatic, requiredLocals, all, frame)


        var usedLocals = frame.locals.size - 1



        if (capturedStack) {
            usedLocals++
            val slot = usedLocals

            captureStack(frame.stack, slot, instructions)
            instructions.add(VarInsnNode(ASTORE, slot))

            slot
        }
        val stackObjSlot = usedLocals


        val capturedLocalSlots = requiredLocals.map {
            val slot = it
            captureLocal(slot, frame.locals[slot]!!, instructions)

            instructions.add(VarInsnNode(ASTORE, slot))

            slot
        }

        val mixinFlowObjSlot = ++usedLocals
        instantiateFlowObj(instructions)
        instructions.add(VarInsnNode(ASTORE, mixinFlowObjSlot))

        // Accumulator
        instructions.add(TypeInsnNode(NEW, MixinFlow.Result::class.internalName))
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

        // At this point the accumulator result (which is just a continue) is in
        // the top of the stack. Each injection call should not modify the stack
        // unless it adds another result on top of it. In that case we fold them
        // together.

        for (data in all) {
            val parameters = determineParameters(
                data.mixinMethod.method(),
                mixinFlowObjSlot,
                stackObjSlot,
                capturedLocalSlots
            ) {
                data.mixinClass
            }

            // Folding if the stack is updated.
            val hasResult =
                callInjection(targetClass, data.mixinMethod.method(), isStatic, parameters, instructions)

            if (hasResult) {
                instructions.add(
                    MethodInsnNode(
                        INVOKEVIRTUAL, MixinFlow.Result::class.internalName, "fold", "(${
                            MixinFlow.Result::class.descriptor
                        })${MixinFlow.Result::class.descriptor}"
                    )
                )
            }
        }

        val labelContinue = LabelNode(Label())

        instructions.add(InsnNode(DUP))
        instructions.add(
            FieldInsnNode(
                GETFIELD,
                MixinFlow.Result::class.internalName,
                "yielding",
                Type.BOOLEAN_TYPE.descriptor
            )
        )
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

        when (returnType?.sort) {
            TypeSort.INT -> {
                getResultValue()
                manuelUnbox(returnType, instructions)
                instructions.add(InsnNode(IRETURN))
            }

            TypeSort.LONG -> {
                getResultValue()
                manuelUnbox(returnType, instructions)
                instructions.add(InsnNode(LRETURN))
            }

            TypeSort.FLOAT -> {
                getResultValue()
                manuelUnbox(returnType, instructions)
                instructions.add(InsnNode(FRETURN))
            }

            TypeSort.DOUBLE -> {
                getResultValue()
                manuelUnbox(returnType, instructions)
                instructions.add(InsnNode(DRETURN))
            }

            TypeSort.OBJECT -> {
                getResultValue()
                manuelUnbox(returnType, instructions)
                instructions.add(InsnNode(ARETURN))
            }

            null -> {
                instructions.add(InsnNode(POP))
                instructions.add(InsnNode(RETURN))
            }
        }

        instructions.add(labelContinue)

        instructions.add(InsnNode(POP)) // Pop the extra 'result' off the top of the stack

        capturedLocalSlots.forEach {
            releaseLocal(it, frame.locals[it]!!, instructions)
        }

        if (capturedStack) {
            releaseStack(stackObjSlot, frame.stack, instructions)
        }

        return instructions
    }

    private fun checkConditions(
        isStatic: Boolean,
        requiredLocals: List<Int>,
        all: List<InstructionInjectionData>,
        frame: SimulatedFrame
    ) {
        if (!isStatic && requiredLocals.contains(0)) {
            val mixin = all.first {
                it.capturedLocals.contains(0)
            }

            throw CodeInjectionException(
                "Mixin: '${mixin.mixinClass}' contains code injection: '${mixin.mixinMethod.method()}' which requests local 0. This method is not static and so cannot capture type 'this'. Locals should be 1 based for non static methods."
            )
        }

        val maxLocal = requiredLocals.maxOrNull() ?: 0
        if (maxLocal > frame.locals.size - 1) {
            val mixin = all.first {
                it.capturedLocals.contains(maxLocal)
            }
            throw CodeInjectionException(
                "Mixin: '${mixin.mixinClass}' contains code injection: '${mixin.mixinMethod.method()}' which requests " +
                        "more locals than are present by its point of injection. The available locals are: '${frame.locals}'. " +
                        "This mixin requests a local of slot: '$maxLocal'."
            )
        }

        val flowResultType = Type.getType(MixinFlow.Result::class.java)

        for (it in all) {
            val returnType = it.mixinMethod.method().returnType

            if (returnType != Type.VOID_TYPE && returnType != flowResultType) {
                throw CodeInjectionException(
                    "Mixin: '${it.mixinClass}' contains code injection: '${it.mixinMethod.method()}' and " +
                            "specifies a return type of '${returnType}'. The return type for a code " +
                            "injection method should either be 'void' or 'MixinFlow.Result'."
                )
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
                methodFromDesc(select.invoke.method),
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

    private fun findDestMethod(
        mixinMethod: Method,
        given: String,
        destination: ClassNode,
        mixinClass: () -> ClassReference
    ): Method {
//        fun isMixinRelatedParameter(type: Type): Boolean {
//            val isLocal = type == Type.getType(Captured::class.java)
//            val isFlow = type == Type.getType(MixinFlow::class.java)
//
//            return isLocal || isFlow
//        }

        val method = if (given.isEmpty()) {
            // TODO infer parameters? possible with extra byte code analysis
            Method(
                mixinMethod.name,
                "()V"
            )
        } else {
            // If it contains a space then parse it semantically, otherwise like an internal method, or just the name
            if (given.contains(" ")) {
                val spaces = given.count { it == ' ' }
                val methodName = if (spaces == 1) {
                    "void $given"
                } else if (spaces == 2) {
                    given
                } else {
                    throw InvalidMixinException(
                        mixinClass(),
                        MixinExceptionCause.CodeInvalidMethodDescription,
                        listOf("<package.ReturnType> <method name> (<package.Parameter...>)")
                    )
                }

                Method.getMethod(methodName)
            } else if (given.contains("(")) {
                methodFromDesc(given)
            } else {
//                val methodArgs = mixinMethod
//                    .argumentTypes
//                    .filterNot(::isMixinRelatedParameter)

                Method(
                    given,
                    "()V"
//                    "(${
//                        methodArgs.joinToString(separator = "") { it.descriptor }
//                    })${mixinMethod.returnType}"
                )
            }
        }

        val overloading = destination.methods
            .map { it.method() }
            .filter { it.name == method.name }

        if (overloading.isEmpty()) {
            throw InvalidMixinException(
                mixinClass(),
                MixinExceptionCause.NoCodeMethodFound,
                listOf(method.name, destination.ref().toString())
            )
        } else if (overloading.size == 1) {
            return overloading[0]
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
                    mixinClass(),
                    MixinExceptionCause.CodeMethodOverloadAmbiguity,
                    listOf(method.name, matchingMethods.toString())
                )
            } else throw InvalidMixinException(
                mixinClass(),
                MixinExceptionCause.NoCodeMethodFound,
                listOf(method.toString(), destination.ref().toString())
            )
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
//        val vars: List<Int>?,
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
    val requestedMethod: String,

    val injectionType: InjectionType,
    val point: InjectionPoint,

    val capturedLocals: List<Int>,
) : InjectionData