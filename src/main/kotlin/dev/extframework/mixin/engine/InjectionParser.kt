package dev.extframework.mixin.engine

import dev.extframework.mixin.annotation.AnnotationTarget
import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.engine.operation.OperationData
import dev.extframework.mixin.engine.tag.ClassTag
import dev.extframework.mixin.engine.transform.ClassTransformer
import org.objectweb.asm.tree.AnnotationNode

/**
 * Injector here only refers to a transformer because that's the only type
 * you can define as an injection.
 */
public interface InjectionParser<T: OperationData> {
    public val transformer: ClassTransformer<T>

    public fun parse(
        tag: ClassTag,
        element: AnnotationTarget,
        annotation: AnnotationNode,
        targets: Set<ClassReference>
    ) : Output<T>

    public data class Output<T: OperationData>(
        val data: T,
        val delta: Set<ClassReference>
    )
}