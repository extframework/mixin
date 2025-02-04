package dev.extframework.mixin.internal.inject.impl.method

import dev.extframework.mixin.BroadApplicator
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.Opcodes
import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.internal.analysis.JvmValueRef
import dev.extframework.mixin.internal.analysis.toValueRef
import dev.extframework.mixin.internal.annotation.AnnotationTarget
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import dev.extframework.mixin.internal.inject.impl.abstact.AbstractInjection
import dev.extframework.mixin.internal.inject.impl.abstact.AbstractMixinInjector
import dev.extframework.mixin.internal.util.internalName
import dev.extframework.mixin.internal.util.manualBox
import dev.extframework.mixin.internal.util.manualUnbox
import dev.extframework.mixin.internal.util.method
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

public class MethodInjector(
    public val redefinitionFlags: RedefinitionFlags,
) : MixinInjector<MethodInjectionData> {
    override fun parse(
        target: AnnotationTarget,
        annotation: AnnotationNode
    ): MethodInjectionData {
        return MethodInjectionData(
            target.methodNode
        )
    }

    override fun inject(
        node: ClassNode,
        all: List<MethodInjectionData>
    ): List<MixinInjector.Residual<*>> {
        return when (redefinitionFlags) {
            RedefinitionFlags.FULL, RedefinitionFlags.NONE -> {
                fullyRedefine(node, all)
            }

            RedefinitionFlags.ONLY_INSTRUCTIONS -> instructionRedefine(node, all)
        }
    }

    private fun fullyRedefine(
        node: ClassNode,
        all: List<MethodInjectionData>
    ): List<MixinInjector.Residual<*>> {
        all.forEach {
            node.methods.add(it.method)
        }

        return listOf()
    }

    private fun instructionRedefine(
        node: ClassNode,
        all: List<MethodInjectionData>
    ): List<MixinInjector.Residual<*>> {
        val all = all.map { it.method }

        val (static, nonStatic) = all.partition {
            it.access and ACC_STATIC != 0
        }

        // Using spaces because we are special
        val methodName = "uber method"
        val staticMethodName = "static uber method"

        val method = buildUberMethod(methodName, false, nonStatic)
        val staticMethod = buildUberMethod(staticMethodName, true, static)

        node.methods.add(method)
        node.methods.add(staticMethod)

        return (static.mapIndexed { i, it ->
            buildRemappingInjector(
                node.name,
                it.method(),
                i,
                staticMethod.method()
            )
        } + nonStatic.mapIndexed { i, it ->
            buildRemappingInjector(
                node.name,
                it.method(),
                i,
                method.method()
            )
        }).map {
            MixinInjector.Residual(
                it,
                BroadApplicator,
                AbstractMixinInjector,
            )
        }
    }

    internal fun buildUberMethod(
        name: String,
        isStatic: Boolean,
        all: List<MethodNode>
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
            LabelNode(Label()) to it
        }
        val defaultLabel = LabelNode(Label())

        val switch = LookupSwitchInsnNode(
            defaultLabel,
            all.indices.toList().toIntArray(),
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
                node.instructions
            )
            transformReturns(
                node.instructions,
                node.maxLocals,
            )
            insn.add(node.instructions)
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
        owner: String,
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
                            it.owner == owner && Method(it.name, it.desc) == original
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
    val method: MethodNode
) : InjectionData