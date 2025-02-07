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
    )

    public fun residualsFor(
        data: T,
        // The applicator used by the injection defining the given data. This method
        // does not have to return a residual using the same applicator
        applicator: MixinApplicator
    ) : List<Residual<*>>

    public data class Residual<T: InjectionData>(
        val data: T,
        val applicator: MixinApplicator,
        val injector: MixinInjector<T>
    )
}