package dev.extframework.mixin.internal.inject

import dev.extframework.mixin.annotation.AnnotationTarget
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

public interface MixinInjector<T: InjectionData> {
    public fun parse(
        target: AnnotationTarget,
        annotation: AnnotationNode,
    ) : T

    public fun inject(
        node: ClassNode,
        helper: InjectionHelper<T>
//        all: List<T>
    )

    public interface InjectionHelper<T: InjectionData> {
        public val allData: Set<T>

        public fun applicable() : List<T>

        public fun <T2: InjectionData> inject(
            node: ClassNode,
            injector: MixinInjector<T2>,
            // Applicable data
            data: List<T2>,
        )
    }

//    public fun residualsFor(
//        data: T,
//        // The applicator used by the injection defining the given data. This method
//        // does not have to return a residual using the same applicator
//        applicator: MixinApplicator
//    ) : List<Residual<*>>

//    public data class Residual<T: InjectionData>(
//        val data: T,
//        val applicator: MixinApplicator,
//        val injector: MixinInjector<T>
//    )
}