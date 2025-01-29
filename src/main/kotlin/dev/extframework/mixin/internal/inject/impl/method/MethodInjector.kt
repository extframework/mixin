package dev.extframework.mixin.internal.inject.impl.method

import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.internal.annotation.AnnotationTarget
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

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

            RedefinitionFlags.ONLY_INSTRUCTIONS -> TODO()
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
}

public data class MethodInjectionData(
    val method: MethodNode
) : InjectionData