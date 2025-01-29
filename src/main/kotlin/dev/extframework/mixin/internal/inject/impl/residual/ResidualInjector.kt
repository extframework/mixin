package dev.extframework.mixin.internal.inject.impl.residual

//public class ResidualInjector : MixinInjector<MixinResidual> {
//    override fun parse(
//        target: AnnotationTarget,
//        annotation: AnnotationNode
//    ): MixinResidual {
//        throw UnsupportedOperationException()
//    }
//
//    override fun inject(
//        node: ClassNode,
//        all: List<MixinResidual>
//    ): List<MixinInjector.Result<*>> {
//        val refTable = ReferenceTable.Companion.new(node)
//
//        all.forEach { t ->
//            t.transform(node, refTable)
//        }
//
//        return listOf()
//    }
//}