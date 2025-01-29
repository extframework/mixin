package dev.extframework.mixin.internal.parse

import dev.extframework.mixin.internal.inject.InjectionData
import org.objectweb.asm.tree.ClassNode

internal data class MixinInfo(
    val node: ClassNode,

//    val applicator: MixinApplicator,

    val injections : List<InjectionData>
)

//internal sealed interface MixinCodeInjection {
//
//}
//
//internal data class

//internal data class CodeInjectionInfo(
//
//)