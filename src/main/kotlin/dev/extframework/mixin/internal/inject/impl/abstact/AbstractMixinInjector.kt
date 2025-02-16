package dev.extframework.mixin.internal.inject.impl.abstact

// Not actually abstract...
//public object AbstractMixinInjector : MixinInjector<AbstractInjection> {
//    override fun parse(
//        target: AnnotationTarget,
//        annotation: AnnotationNode
//    ): AbstractInjection {
//        throw UnsupportedOperationException()
//    }
//
//    override fun inject(
//        node: ClassNode,
//        all: List<AbstractInjection>
//    ) {
//        all.forEach {
//            it.inject(node)
//        }
//    }
//
//    override fun residualsFor(
//        data: AbstractInjection,
//        applicator: MixinApplicator
//    ): List<MixinInjector.Residual<*>> {
//        return listOf()
//    }
//}
//
//public interface AbstractInjection : InjectionData {
//    public fun inject(node: ClassNode)
//}