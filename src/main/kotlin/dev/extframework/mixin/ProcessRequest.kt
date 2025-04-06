package dev.extframework.mixin

import dev.extframework.mixin.api.ClassReference
import org.objectweb.asm.tree.ClassNode

//public sealed interface ProcessRequest {
//    public val reference: ClassReference
//
//    public val node: ClassNode?
//        get() = null
//
//    public data class Generate(
//        override val reference: ClassReference
//    ) : ProcessRequest
//
//    public data class Transform(
//        override val node: ClassNode
//    ) : ProcessRequest {
//        override val reference: ClassReference = ClassReference(node.name)
//    }
//}

