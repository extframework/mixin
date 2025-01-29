package dev.extframework.mixin.internal.inject

import dev.extframework.mixin.api.MixinApplicator
import dev.extframework.mixin.internal.annotation.AnnotationTarget
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

public interface MixinInjector<T: InjectionData> {
    public fun parse(
        target: AnnotationTarget,
        annotation: AnnotationNode,
    ) : T

    public fun inject(
        node: ClassNode,
        all: List<T>
    ): List<Residual<*>>

    public data class Residual<T: InjectionData>(
        val data: T,
        val applicator: MixinApplicator,
        val injector: MixinInjector<T>
    )
}