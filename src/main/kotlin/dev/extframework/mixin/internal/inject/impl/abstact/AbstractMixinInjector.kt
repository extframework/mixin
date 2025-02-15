package dev.extframework.mixin.internal.inject.impl.abstact

import dev.extframework.mixin.api.MixinApplicator
import dev.extframework.mixin.internal.annotation.AnnotationTarget
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

// Not actually abstract...
public object AbstractMixinInjector : MixinInjector<AbstractInjection> {
    override fun parse(
        target: AnnotationTarget,
        annotation: AnnotationNode
    ): AbstractInjection {
        throw UnsupportedOperationException()
    }

    override fun inject(
        node: ClassNode,
        all: List<AbstractInjection>
    ) {
        all.forEach {
            it.inject(node)
        }
    }

    override fun residualsFor(
        data: AbstractInjection,
        applicator: MixinApplicator
    ): List<MixinInjector.Residual<*>> {
        return listOf()
    }
}

public interface AbstractInjection : InjectionData {
    public fun inject(node: ClassNode)
}