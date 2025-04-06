package dev.extframework.mixin.annotation

import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

public interface AnnotationProcessor {
    public fun process(
        classNode: ClassNode,
        annotation: Class<out Annotation>,
    ): List<AnnotationElement>

    public fun  processAll(
        classNode: ClassNode,
    ): List<AnnotationElement>

    public data class AnnotationElement(
        val annotation: AnnotationNode,
        val target: AnnotationTarget,
    )
}