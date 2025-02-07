package dev.extframework.mixin.internal.inject.impl.method

import dev.extframework.mixin.BroadApplicator
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.MixinApplicator
import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.internal.analysis.JvmValueRef
import dev.extframework.mixin.internal.analysis.toValueRef
import dev.extframework.mixin.internal.annotation.AnnotationTarget
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import dev.extframework.mixin.internal.inject.impl.abstact.AbstractInjection
import dev.extframework.mixin.internal.inject.impl.abstact.AbstractMixinInjector
import dev.extframework.mixin.internal.util.clone
import dev.extframework.mixin.internal.util.internalName
import dev.extframework.mixin.internal.util.manualBox
import dev.extframework.mixin.internal.util.manualUnbox
import dev.extframework.mixin.internal.util.method
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*

private const val METHOD_NAME = "uber method"
private const val STATIC_METHOD_NAME = "static uber method"

public class MethodInjector(
    public val redefinitionFlags: RedefinitionFlags,
) : MixinInjector<MethodInjectionData> {

    public companion object {
        private var idCounter: Int = 0

        public fun buildData(
            method: MethodNode
        ) : MethodInjectionData {
            return MethodInjectionData(
                method,
                idCounter++,
            )
        }
    }

    override fun parse(
        target: AnnotationTarget,
        annotation: AnnotationNode
    ): MethodInjectionData {
        return buildData(target.methodNode)
    }

    override fun inject(
        node: ClassNode,
        all: List<MethodInjectionData>
    ) {
        when (redefinitionFlags) {
            RedefinitionFlags.FULL, RedefinitionFlags.NONE -> {
                fullyRedefine(node, all)
            }

            RedefinitionFlags.ONLY_INSTRUCTIONS -> instructionRedefine(node, all)
        }
    }

    override fun residualsFor(
        data: MethodInjectionData,
        applicator: MixinApplicator
    ): List<MixinInjector.Residual<*>> {
        return when (redefinitionFlags) {
            RedefinitionFlags.FULL, RedefinitionFlags.NONE -> listOf()

            RedefinitionFlags.ONLY_INSTRUCTIONS -> {
                val isStatic = data.method.access and ACC_STATIC != 0

                listOf(
                    MixinInjector.Residual(
                        buildRemappingInjector(
                            applicator,
                            data.method.method(),
                            data.uniqueId,
                            Method(
                                if (isStatic) STATIC_METHOD_NAME else METHOD_NAME,
                                "(I[Ljava/lang/Object;)Ljava/lang/Object;"
                            )
                        ),
                        BroadApplicator,
                        AbstractMixinInjector,
                    )
                )
            }
        }
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
        node: ClassNode,
        all: List<MethodInjectionData>
    ) {
        val (static, nonStatic) = all.partition {
            it.method.access and ACC_STATIC != 0
        }

        // Using spaces because we are special
        val methodName = METHOD_NAME
        val staticMethodName = STATIC_METHOD_NAME

        val method = buildUberMethod(methodName, false, nonStatic)
        val staticMethod = buildUberMethod(staticMethodName, true, static)

        node.methods.add(method)
        node.methods.add(staticMethod)
    }

    internal fun buildUberMethod(
        name: String,
        isStatic: Boolean,
        all: List<MethodInjectionData>
    ): MethodNode {
        val node = MethodNode(
            ACC_PUBLIC or if (isStatic) ACC_STATIC else 0,
            name,
            "(I[Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            emptyArray(),
        )

        val insn = InsnList()

        val labels: List<Pair<LabelNode, MethodNode>> = all.map {
            LabelNode(Label()) to it.method
        }
        val defaultLabel = LabelNode(Label())

        val switch = LookupSwitchInsnNode(
            defaultLabel,
            all.map { it.uniqueId }.toIntArray(),
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
            var method = node.method()

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

    private fun buildRemappingInjector(
        applicator: MixinApplicator,
        original: Method,
        ordinal: Int,
        remapped: Method,
    ): AbstractInjection = object : AbstractInjection {
        override fun inject(node: ClassNode) {
            node.methods
                .forEach { method ->
                    val instructions = method.instructions

                    instructions
                        .asSequence()
                        .filterIsInstance<MethodInsnNode>()
                        .filter {
                            applicator.applies(ClassReference(it.owner)) && Method(it.name, it.desc) == original
                        }
                        .forEach { call ->
                            val injection = buildMethodCall(method.maxLocals, ordinal, original)
                            instructions.insertBefore(call, injection)

                            val unwrappingInjection = buildMethodUnwrap(
                                original.returnType.takeUnless { it == Type.VOID_TYPE }?.toValueRef()
                            )
                            instructions.insert(call, unwrappingInjection)


                            call.name = remapped.name
                            call.desc = remapped.descriptor
                        }
                }

        }
    }

    internal fun buildMethodCall(
        maxLocals: Int,
        ordinal: Int,
        method: Method
    ): InsnList {
        val insn = InsnList()

        val params = method.argumentTypes.map { it.toValueRef() }

        var arraySlot = maxLocals

        insn.add(IntInsnNode(BIPUSH, params.size))
        insn.add(TypeInsnNode(ANEWARRAY, Any::class.internalName))
        insn.add(VarInsnNode(ASTORE, arraySlot))

        params.forEachIndexed { i, param ->
            insn.add(VarInsnNode(ISTORE + param.sort.offset, maxLocals + i + 1))
            insn.add(VarInsnNode(ALOAD, arraySlot))
            insn.add(IntInsnNode(BIPUSH, params.size - 1 - i))
            manualBox(param.sort, maxLocals + i + 1, insn)
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

public data class MethodInjectionData(
    // Note that this method should NEVER be mutated if you want reloading to be safe.
    val method: MethodNode,
    val uniqueId: Int,
) : InjectionData