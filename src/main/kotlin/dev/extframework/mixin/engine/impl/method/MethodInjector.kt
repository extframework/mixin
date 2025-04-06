package dev.extframework.mixin.engine.impl.method

import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.engine.analysis.JvmValueRef
import dev.extframework.mixin.engine.analysis.toValueRef
import dev.extframework.mixin.engine.operation.OperationParent
import dev.extframework.mixin.engine.operation.OperationParent.Companion.parents
import dev.extframework.mixin.engine.operation.TargetedMixinRegistry
import dev.extframework.mixin.engine.transform.ClassTransformer
import dev.extframework.mixin.engine.util.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*

private const val METHOD_NAME = "uber_method_"
private const val STATIC_METHOD_NAME = "static_uber_method_"

public open class MethodInjector(
    public open val redefinitionFlags: RedefinitionFlags,
) : ClassTransformer<MethodInjectionData> {
    override val registry: TargetedMixinRegistry<MethodInjectionData> = TargetedMixinRegistry<MethodInjectionData>(
        listOf(), {
            listOf()
        }, { it.targets }
    )
    override val parents: Set<OperationParent> = parents()

    override fun transform(
        node: ClassNode,
    ): ClassNode {
        val applicable = registry.applicable(node.ref())

        if (node.access and ACC_INTERFACE == ACC_INTERFACE) {
            return node
        }

        when (redefinitionFlags) {
            RedefinitionFlags.FULL, RedefinitionFlags.NONE -> {
                fullyRedefine(node, applicable)

                node
            }

            RedefinitionFlags.ONLY_INSTRUCTIONS -> {
                val uniqueClassId = node.name.replace('/', '_')

                val methodName = METHOD_NAME + uniqueClassId
                val staticMethodName = STATIC_METHOD_NAME + uniqueClassId

                val result = instructionRedefine(methodName, staticMethodName, node, applicable)

                val (static, notStatic) = registry.all().partition {
                    it.method.access and ACC_STATIC != 0
                }

                remap(
                    static,
                    Method(
                        staticMethodName,
                        "(I[Ljava/lang/Object;)Ljava/lang/Object;"
                    ),
                    result
                )

                remap(
                    notStatic,
                    Method(
                        methodName,
                        "(I[Ljava/lang/Object;)Ljava/lang/Object;"
                    ),
                    result
                )

                result
            }
        }

        return node
    }

    private fun fullyRedefine(
        node: ClassNode,
        all: List<MethodInjectionData>
    ) {
        all.forEach {
            // This is safe, because full reloads should only occur once.
            // If we ever need to do a second 'full' reload, this value should
            // be cloned.
            node.methods.add(it.method)
        }
    }

    private fun instructionRedefine(
        methodName: String,
        staticMethodName: String,
        node: ClassNode,
        all: List<MethodInjectionData>
    ): ClassNode {
        val (static, nonStatic) = all.partition {
            it.method.access and ACC_STATIC != 0
        }

        val method = buildUberMethod(methodName, false, nonStatic)
        val staticMethod = buildUberMethod(staticMethodName, true, static)

        node.methods.add(method)
        node.methods.add(staticMethod)

        return node
    }

    internal fun buildUberMethod(
        name: String,
        isStatic: Boolean,
        all: List<MethodInjectionData>
    ): MethodNode {
        val node = MethodNode(
            ACC_PRIVATE or if (isStatic) ACC_STATIC else 0,
            name,
            "(I[Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            emptyArray(),
        )

        val insn = InsnList()

        val  sortedAll = all.sortedBy { it.uniqueId }

        val labels: List<Pair<LabelNode, MethodNode>> = sortedAll.map {
            LabelNode(Label()) to it.method
        }
        val defaultLabel = LabelNode(Label())

        val switch = LookupSwitchInsnNode(
            defaultLabel,
            sortedAll.map { it.uniqueId }.toIntArray(),
            labels.map { it.first }.toTypedArray(),
        )
        insn.add(VarInsnNode(ILOAD, if (isStatic) 0 else 1))
        insn.add(switch)
        insn.add(defaultLabel)
        insn.add(TypeInsnNode(NEW, Exception::class.internalName))
        insn.add(InsnNode(DUP))
        insn.add(LdcInsnNode("Invalid method call"))
        insn.add(MethodInsnNode(INVOKESPECIAL, Exception::class.internalName, "<init>", "(Ljava/lang/String;)V"))
        insn.add(InsnNode(ATHROW))

        labels.forEach { (label, node) ->
            insn.add(label)
            val  method = node.method()

            val thisInsn = node.instructions.clone()

            loadParameters(
                insn,
                if (isStatic) 1 else 2,
                method.argumentTypes.map {
                    it.toValueRef()
                }
            )
            incrementLocalsAfter(
                if (isStatic) -1 else 0,
                2,
                thisInsn
            )
            transformReturns(
                thisInsn,
                node.maxLocals,
            )
            insn.add(thisInsn)
        }

        node.instructions = insn

        return node
    }

    private fun transformReturns(
        insn: InsnList,
        maxLocals: Int,
    ) {
        insn
            .asSequence()
            .filterIsInstance<InsnNode>()
            .filter { (IRETURN..RETURN).contains(it.opcode) }
            .forEach {
                if (it.opcode == RETURN) {
                    insn.insertBefore(it, InsnNode(ACONST_NULL))
                    insn.insertBefore(it, InsnNode(ARETURN))
                } else {
                    val sort = TypeSort.entries[it.opcode - IRETURN]

                    val newInsn = InsnList()
                    newInsn.add(VarInsnNode(ISTORE + sort.offset, maxLocals))

                    manualBox(sort, maxLocals, newInsn)
                    newInsn.add(InsnNode(ARETURN))

                    insn.insertBefore(it, newInsn)
                }
                insn.remove(it)
            }
    }

    private fun loadParameters(
        insn: InsnList,
        slot: Int,
        parameters: List<JvmValueRef>
    ) {
        parameters.forEachIndexed { i, p ->
            insn.add(LabelNode(Label()))

            insn.add(VarInsnNode(ALOAD, slot))

            insn.add(IntInsnNode(BIPUSH, i))
            insn.add(InsnNode(AALOAD))

            manualUnbox(p, insn)
            // Offset by one to account for the [Ljava/lang/Object;
            insn.add(VarInsnNode(ISTORE + p.sort.offset, slot + i + 1))
        }
    }

    private fun incrementLocalsAfter(
        int: Int,
        incr: Int,
        insn: InsnList
    ) {
        insn.forEach { t ->
            if (t is VarInsnNode && t.`var` > int) {
                t.`var` += incr
            }
        }
    }

    private fun remap(
        data: List<MethodInjectionData>,

//        cls: MixinApplicator,
//        original: Method,
//        ordinal: Int,
        remapped: Method,

        node: ClassNode,
    ) {
        val methodIds = data.associateBy { it.method.method() }

        node.methods
            .forEach { method ->
                val instructions = method.instructions

                val locals = LocalTracker.calculateFor(method)

                instructions
                    .asSequence()
                    .filterIsInstance<MethodInsnNode>()
                    .filter { call ->
                        node.name == call.owner && methodIds.contains(Method(call.name, call.desc))
                    }
                    .forEach { call ->
                        val  methodRef = Method(call.name, call.desc)
                        val data = methodIds[methodRef]!!

                        val injection = buildMethodCall(locals, data.uniqueId, methodRef)
                        instructions.insertBefore(call, injection)

                        val unwrappingInjection = buildMethodUnwrap(
                            methodRef.returnType.takeUnless { it == Type.VOID_TYPE }?.toValueRef()
                        )
                        instructions.insert(call, unwrappingInjection)


                        call.name = remapped.name
                        call.desc = remapped.descriptor
                    }
            }

    }

    internal fun buildMethodCall(
        locals: LocalTracker,
        ordinal: Int,
        method: Method
    ): InsnList {
        val insn = InsnList()

        val params = method.argumentTypes.map { it.toValueRef() }

        val  arraySlot = locals.increment(TypeSort.OBJECT)

        insn.add(IntInsnNode(BIPUSH, params.size))
        insn.add(TypeInsnNode(ANEWARRAY, Any::class.internalName))
        insn.add(VarInsnNode(ASTORE, arraySlot))

        params.forEachIndexed { i, param ->
            val  currSlot = locals.increment(param.sort)

            insn.add(VarInsnNode(ISTORE + param.sort.offset, currSlot))
            insn.add(VarInsnNode(ALOAD, arraySlot))
            insn.add(IntInsnNode(BIPUSH, params.size - 1 - i))
            manualBox(param.sort, currSlot, insn)
            insn.add(InsnNode(AASTORE))
        }

        insn.add(IntInsnNode(BIPUSH, ordinal))
        insn.add(VarInsnNode(ALOAD, arraySlot))

        return insn
    }

    internal fun buildMethodUnwrap(
        expected: JvmValueRef?,
    ): InsnList {
        val list = InsnList()

        if (expected == null) {
            list.add(InsnNode(POP))
        } else {
            manualUnbox(expected, list)
        }

        return list
    }
}

