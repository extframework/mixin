package dev.extframework.mixin.internal.inject.residual

import dev.extframework.mixin.api.MixinApplicator
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.ref.ReferenceTable
import org.objectweb.asm.tree.ClassNode

//public interface MixinResidual : InjectionData {
//    public val applicator: MixinApplicator
//
//    public fun transform(
//        node: ClassNode,
//        refTable: ReferenceTable,
//    )
//}