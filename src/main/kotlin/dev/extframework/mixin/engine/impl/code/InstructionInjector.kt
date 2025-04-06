package dev.extframework.mixin.engine.impl.code

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.MixinExceptionCause
import dev.extframework.mixin.api.*
import dev.extframework.mixin.engine.analysis.*
import dev.extframework.mixin.engine.transform.ClassTransformer
import dev.extframework.mixin.engine.operation.TargetedMixinRegistry
import dev.extframework.mixin.engine.impl.method.MethodInjectionData
import dev.extframework.mixin.engine.impl.method.MethodInjector
import dev.extframework.mixin.engine.operation.OperationParent
import dev.extframework.mixin.engine.operation.OperationParent.Companion.parents
import dev.extframework.mixin.engine.util.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*

public class InstructionInjector(
    methodInjector: ClassTransformer<MethodInjectionData>
) : ClassTransformer<InstructionInjectionData> {
    override val registry: TargetedMixinRegistry<InstructionInjectionData> = TargetedMixinRegistry(
        listOf(methodInjector.registry),
        {
            listOf(it.mixinMethod)
        }
    ) {
        it.targets
    }

    override val parents: Set<OperationParent> = parents(
        methodInjector to OperationParent.Order.AFTER
    )

    override fun transform(
        node: ClassNode
    ): ClassNode {
        val applicable = registry.applicable(node.ref())

        if (node.access and ACC_INTERFACE == ACC_INTERFACE) {
            if (applicable.isNotEmpty()) {
                val erroneous = applicable.first()

                throw InvalidMixinException(
                    erroneous.mixinClass,
                    MixinExceptionCause.CodeCannotApplyToInterfaces,
                    erroneous.mixinMethod.method.method()
                )
            }

            return node
        }

        val result = preprocessInjections(
            node,
            applicable
        )

        return result
    }

    // -------------------------------------
    // +++++++++++++ Internal ++++++++++++++
    // -------------------------------------

    private data class FlowTarget(
        val method: MethodNode,
        val start: AbstractInsnNode,
        val end: AbstractInsnNode
    )

    private data class BuiltFlow(
        val type: InjectionType,
        val target: FlowTarget,
        val instructions: InsnList
    )

    internal fun preprocessInjections(
        node: ClassNode,
        all: List<InstructionInjectionData>
    ): ClassNode {
        val injectionsByMethod: Map<Method, List<InstructionInjectionData>> = all.groupBy {
            findTargetMethod(
                it.inferredTarget,
                node.methods.map { it.method() },
            ) { it.mixinClass to node.ref() }
        }

        val builtFlows = injectionsByMethod.flatMap { (method, toInject: List<InstructionInjectionData>) ->
            val methodNode = node.methods.first { it.method() == method }
            val methodDescriptor = methodNode.method()
            val isStatic = methodNode.access and ACC_STATIC == ACC_STATIC
            val locals = LocalTracker.calculateFor(methodNode)

            val placedInjections: List<Pair<InstructionInjectionData, InjectionPoint.Group>> = toInject.flatMap {
                it.point.getPoints(methodNode, node)
                    .map { insn -> it to insn }
            }

            placedInjections
                .groupBy { (data, node) -> node to data.injectionType }
                .mapValues { (_, value) -> value.map { it.first } }
                .map { (point: Pair<InjectionPoint.Group, InjectionType>, toInject: List<InstructionInjectionData>) ->
                    val (group, type) = point

                    val targetInstructions = methodNode.instructions

                    var targetPoint = when (type) {
                        InjectionType.BEFORE -> group.start
                        InjectionType.AFTER -> group.end.next
                        // The values will get overwritten so we cant put it at the end
                        InjectionType.OVERWRITE -> group.start
                    }

                    val frame = analyzeFrames(
                        targetPoint,
                        SimulatedFrame(
                            listOf(),
                            (if (isStatic) listOf() else listOf(
                                ObjectValueRef(Type.getObjectType(node.name))
                            )) + methodDescriptor.argumentTypes.map { it.toValueRef() }
                        )
                    )

                    // Check that these injections are valid
                    checkValidity(
                        toInject,
                        isStatic,
                        frame
                    )

                    val allowReturnType = if (methodNode.name == "<init>") {
                        val initialization = getConstructorInitializationInstruction(targetInstructions, node)

                        if (initialization != null) {
                            val targetIndex = run {
                                val index = targetInstructions.indexOf(
                                    targetPoint
                                )

                                when (type) {
                                    InjectionType.BEFORE -> index - 1
                                    InjectionType.AFTER -> index + 1
                                    InjectionType.OVERWRITE -> index
                                }
                            }

                            targetInstructions.indexOf(
                                initialization
                            ) < targetIndex
                        } else true
                    } else true

                    val flowInsn = buildMixinFlow(
                        toInject,
                        frame,
                        locals,
                        node.ref(),
                        methodDescriptor.returnType
                            ?.takeUnless { it == Type.VOID_TYPE }
                            ?.toValueRef(),
                        allowReturnType
                    )

                    BuiltFlow(
                        type,
                        FlowTarget(
                            methodNode,
                            group.start,
                            group.end
                        ),
                        flowInsn
                    )
                }
        }

        builtFlows
            .groupBy {
                it.target.method
            }
            .forEach { (method, flows) ->
                flows.forEach { flow ->
                    when (flow.type) {
                        InjectionType.BEFORE -> {
                            method.instructions.insertBefore(
                                flow.target.start,
                                flow.instructions
                            )
                        }

                        InjectionType.AFTER -> {
                            method.instructions.insert(flow.target.end, flow.instructions)
                        }

                        InjectionType.OVERWRITE -> {
                            method.instructions.insert(flow.target.end, flow.instructions)

                            removeAll(
                                flow.target.start,
                                flow.target.end,
                                method.instructions
                            )
                        }
                    }
                }
            }

        return node
    }

    internal fun buildMixinFlow(
        all: List<InstructionInjectionData>,

        frame: SimulatedFrame,
        locals: LocalTracker,

        targetClass: ClassReference,
        returnType: JvmValueRef?,
        returnAllowed: Boolean
    ): InsnList {
        val instructions = InsnList()

        // Check if any injection needs the stack captured
        val capturesStack = all.any {
            it.mixinMethod.method.method().argumentTypes.any {
                it == Type.getType(Stack::class.java)
            }
        }

        /* Check which locals need to be captured:
           --> We do this by looking not for the slot of the local variable,
               but its index relative to other local variables (thus the sorting
               and effective array access by index)
         */
        val requiredLocals = run {
            all.flatMapTo(HashSet()) {
                it.capturedLocals
            }
        }

        // --------------------------------------------------
        // ++++++++++++++++ STACK CAPTURE +++++++++++++++++++
        // --------------------------------------------------

        // Stack slot, this won't be a valid value if capturedStack is not true
        val stackObjSlot = if (capturesStack) {
            // Increment slot and capture
            val slot = locals.increment(
                TypeSort.OBJECT
            )

            instructions.add(LabelNode(Label()))

            // Capture the stack and store the resulting value as a local.
            captureStack(
                frame.stack,
                locals,
                instructions
            )
            instructions.add(VarInsnNode(ASTORE, slot))

            slot
        } else -1

        // --------------------------------------------------
        // +++++++++++++++++ STACK LOCALS +++++++++++++++++++
        // --------------------------------------------------

        // Similar to before, increment used locals, add label, capture local, and store.
        val capturedLocalSlots = requiredLocals.map { original ->
            val localType = frame.locals[original]!!

            val slot = locals.increment(TypeSort.OBJECT)

            instructions.add(LabelNode(Label()))

            captureLocal(original, localType, instructions)

            instructions.add(VarInsnNode(ASTORE, slot))

            slot to original
        }

        // --------------------------------------------------
        // +++++++++++++ CONSTRUCT MIXIN FLOW +++++++++++++++
        // --------------------------------------------------

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

        // --------------------------------------------------
        // ++++++++++++++++++ Call Flows ++++++++++++++++++++
        // --------------------------------------------------

        // At this point the accumulator result (which is just a 'continue') is stored
        // in the local variables. Each injection call should not modify the stack
        // unless it adds another result on top of it. In that case we fold them
        // together and store the new accumulator.

        for (data in all) {
            val parameters = determineParameters(
                data.mixinMethod.method.method(),
                mixinFlowObjSlot,
                stackObjSlot,
                capturedLocalSlots.map { it.first }
            )

            instructions.add(LabelNode(Label()))
            // If this mixin has a flow result then we want to load the accumulator
            // flow and fold, if not then those steps can be ignored.
            val hasResult = hasFlowResult(data.mixinMethod.method.method())

            if (hasResult) instructions.add(VarInsnNode(ALOAD, accumulatorSlot))

            // Folding if the stack is updated.
            callInjection(
                targetClass,
                data.mixinMethod.method.method(),
                data.mixinMethod.method.access.and(ACC_STATIC) == ACC_STATIC,
                parameters,
                instructions
            )

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

        // --------------------------------------------------
        // ++++++++++++++++ Jump and Return +++++++++++++++++
        // --------------------------------------------------

        val labelContinue = LabelNode(Label())

        // Load the accumulator, dup it, and see if it yields (wants a return)
        instructions.add(VarInsnNode(ALOAD, accumulatorSlot))
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

        // Based on the return type of the method (maybe box) and return.
        instructions.add(LabelNode(Label()))

        if (returnAllowed)
            doReturn(
                returnType,
                instructions,
                accumulatorSlot
            )
        else {
            instructions.add(TypeInsnNode(NEW, Exception::class.internalName))
            instructions.add(InsnNode(DUP))
            instructions.add(LdcInsnNode("Invalid yield! You cannot return yet at this point in the constructor."))
            instructions.add(
                MethodInsnNode(
                    INVOKESPECIAL,
                    Exception::class.internalName,
                    "<init>",
                    "(${String::class.descriptor})V"
                )
            )
            instructions.add(InsnNode(ATHROW))
        }

        // --------------------------------------------------
        // +++++++++++++++++ Release Locals +++++++++++++++++
        // --------------------------------------------------

        instructions.add(labelContinue)

        capturedLocalSlots.forEach { (objSlot, original) ->
            instructions.add(LabelNode(Label()))
            releaseLocal(objSlot, original, frame.locals[original]!!, instructions)
        }

        // --------------------------------------------------
        // ++++++++++++++++++ Release Stack +++++++++++++++++
        // --------------------------------------------------

        if (capturesStack) {
            instructions.add(LabelNode(Label()))
            releaseStack(
                stackObjSlot,
                frame.stack,
                instructions
            )
        }

        return instructions
    }

    private fun checkValidity(
        allData: List<InstructionInjectionData>,
        isStatic: Boolean,
        frame: SimulatedFrame,
    ) {
        val conflicting =
            allData.any { it.injectionType == InjectionType.OVERWRITE } && allData.size > 1

        if (conflicting) {
            throw InvalidMixinException(
                allData.first {
                    it.injectionType == InjectionType.OVERWRITE
                }.mixinClass,
                MixinExceptionCause.ConflictingCodeInjections,
                allData.joinToString("; ") {
                    "Mixin class: ${it.mixinClass}, Method: ${it.mixinMethod.method.method()}, Type: ${it.injectionType}"
                }
            )
        }

        for (data in allData) {
            val isMixinMethodStatic = data.mixinMethod.method.access.and(ACC_STATIC) == ACC_STATIC

            if (isStatic && !isMixinMethodStatic) {
                throw InvalidMixinException(
                    data.mixinClass,
                    MixinExceptionCause.CodeShouldBeStatic,
                    data.mixinMethod.method.method().toString()
                )
            }

            val flowResultType = Type.getType(MixinFlow.Result::class.java)

            for (type in data.mixinMethod.method.method().argumentTypes) {
                when (type) {
                    // All valid
                    Type.getType(MixinFlow::class.java) -> {}
                    Type.getType(Stack::class.java) -> {}
                    Type.getType(Captured::class.java) -> {}

                    // Definitely not valid...
                    else -> throw InvalidMixinException(
                        data.mixinClass,
                        MixinExceptionCause.CodeInvalidParameterType,
                        data.mixinClass,
                        data.mixinMethod.method.method(),
                        type
                    )
                }
            }

            // Check which locals need to be captured
//            val locals = run {
//                val sortedLocals = frame.locals
//                    .entries
//                    .sortedBy { it.key }
//
//                data.capturedLocals
//                    .sorted()
//                    .map {
//                        sortedLocals[
//                            it
//                        ].key
//                    }
//            }

            // Check that no local exceeds the max local size
            val maxRequestedLocals = data.capturedLocals.maxOrNull() ?: 0
            if (maxRequestedLocals > (frame.locals.keys.maxOrNull() ?: 0)) {
                throw InvalidMixinException(
                    data.mixinClass,
                    MixinExceptionCause.CodeWrongNumLocals,

                    data.mixinClass,
                    data.mixinMethod.method.method(),
                    frame.locals,
                    maxRequestedLocals
                )
            }

            // Check that the mixin returns either void or MixinFlow.Result
            val returnType = data.mixinMethod.method.method().returnType

            if (returnType != Type.VOID_TYPE && returnType != flowResultType) {
                throw InvalidMixinException(
                    data.mixinClass,
                    MixinExceptionCause.CodeWrongReturnType,
                    data.mixinClass,
                    data.mixinMethod.method.method(),
                    returnType
                )
            }

            val signature = data.mixinMethod.method.signature
            if (signature != null) {
                // Infer locals
                val inferredLocals = inferLocalsFromSignature(
                    signature
                )

                // Check that the mixin defines as many 'Captured's as it requests in the annotation.
                if (inferredLocals.size != data.capturedLocals.size) {
                    throw InvalidMixinException(
                        data.mixinClass,
                        MixinExceptionCause.CodeLocalAnnotationParameterMismatch,
                        data.mixinClass,
                        data.mixinMethod.method.method(),
                        data.capturedLocals.size,
                        inferredLocals.size
                    )
                }

                // Check that the types are at least boxable to each other
                data.capturedLocals
                    .zip(inferredLocals)
                    .map { (slot, inferred) ->
                        IndexedValue(slot, frame.locals[slot]!! to inferred.toValueRef())
                    }
                    .forEach { (slot, it) ->
                        val (actual: JvmValueRef, inferred: JvmValueRef) = it
                        if (!inferred.boxableTo(actual)) {
                            // Pretty messy might want to rewrite this exception
                            throw InvalidMixinException(
                                data.mixinClass,
                                MixinExceptionCause.CodeWrongLocalType,
                                data.mixinClass,
                                data.mixinMethod.method.method(),
                                slot,
                                slot - 1,
                                inferred,
                                slot,
                                actual
                            )
                        }
                    }
            }
        }
    }
}