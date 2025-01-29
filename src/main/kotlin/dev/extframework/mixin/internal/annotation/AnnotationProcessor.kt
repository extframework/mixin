package dev.extframework.mixin.internal.annotation

import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

public interface AnnotationProcessor {

//    public fun <T: Annotation> process(
//        archive: ArchiveReference,
//        annotation: Class<T>,
//    ) : List<AnnotationElement<T>>

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