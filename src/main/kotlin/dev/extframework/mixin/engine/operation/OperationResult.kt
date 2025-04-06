package dev.extframework.mixin.engine.operation

import org.objectweb.asm.tree.ClassNode

//public data class OperationResult(
//    val node: ClassNode,
//    val parents: Set<OperationParent>
//) {
//
//}

//public fun ClassTransformation(
//    injection: (ClassNode) -> ClassNode,
//): OperationResult = ClassTransformation(setOf(), injection)
//
//public fun ClassTransformation(
//    vararg parents: OperationParent,
//    injection: (ClassNode) -> ClassNode,
//): OperationResult = ClassTransformation(
//    parents.toSet(), injection
//)
//
//public fun ClassTransformation(
//    parents: Set<OperationParent>,
//    injection: (ClassNode) -> ClassNode,
//): OperationResult = object : OperationResult {
//    override val parents: Set<OperationParent> = parents
//    override fun transform(node: ClassNode): ClassNode = injection(node)
//}
//
//public fun emptyOperation(): OperationResult = object : OperationResult {
//    override val parents: Set<Nothing> = setOf()
//    override fun transform(node: ClassNode): ClassNode {
//        return node
//    }
//}