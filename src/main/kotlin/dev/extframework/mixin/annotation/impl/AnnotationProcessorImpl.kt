package dev.extframework.mixin.annotation.impl

import dev.extframework.mixin.annotation.AnnotationProcessor
import dev.extframework.mixin.annotation.AnnotationTarget
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

internal class AnnotationProcessorImpl(
    private val annotationClsLoader: (String) -> Class<out Annotation> = {
        AnnotationProcessor::class.java.classLoader.loadClass(it) as Class<out Annotation>
    }
) : AnnotationProcessor {
//    private val processedAnnotations = HashMap<ClassID, List<AnnotationProcessor.AnnotationElement<*>>>()

//    override fun <T : Annotation> process(
////        archive: ArchiveReference,
//        annotation: Class<T>
//    ): List<AnnotationProcessor.AnnotationElement<T>> {
//        return archive.reader.entries()
//            .filter { it.name.endsWith(".class") }
//            .flatMap { process(it.open().parseNode(), annotation) }
//            .toList()
//    }

    override fun process(
        classNode: ClassNode,
        annotation: Class<out Annotation>
    ): List<AnnotationProcessor.AnnotationElement> {
        return processAll(classNode).filter {
            annotation.isInstance(it.annotation)
        }
    }

    override fun processAll(
        classNode: ClassNode,
    ): List<AnnotationProcessor.AnnotationElement> {
        fun List<AnnotationNode>.toElements(target: AnnotationTarget): List<AnnotationProcessor.AnnotationElement> {
            return map {
                    AnnotationProcessor.AnnotationElement(
                        it,
                        target
                    )
                }
        }

        return run {
            // TODO type annotations?
            val classElements =
                (classNode.visibleAnnotations ?: listOf())// + (classNode.visibleTypeAnnotations ?: listOf()))
                    .toElements(AnnotationTarget(AnnotationTarget.ElementType.CLASS, classNode))
            val fieldElements = classNode.fields.flatMap { field ->
                (field.visibleAnnotations ?: listOf())// + (field.visibleTypeAnnotations ?: listOf()))

                    .toElements(AnnotationTarget(AnnotationTarget.ElementType.FIELD, classNode, _fieldNode = field))
            }
            val methodElements = classNode.methods.flatMap { method ->
                (method.visibleAnnotations ?: listOf())// + (method.visibleTypeAnnotations ?: listOf()))
                    .toElements(AnnotationTarget(AnnotationTarget.ElementType.METHOD, classNode, method))
            }
            val parameterElements = classNode.methods.flatMap { method ->
                (method.visibleParameterAnnotations ?: arrayOf()).withIndex().flatMap {
                    it.value
                        .toElements(
                        AnnotationTarget(
                            AnnotationTarget.ElementType.PARAMETER,
                            classNode, method, _parameter = it.index
                        )
                    )
                }
            }
            classElements + fieldElements + methodElements + parameterElements
        }
    }

//    private fun ClassNode.id(): ClassID {
//        val writer = ClassWriter(0)
//
//        return ClassID(
//            name,
//            interfaces ?: listOf(),
//            superName,
//            fields.map { it.name },
//            methods.map { it.name },
//            methods.flatMap { it.instructions.map { it.opcode } }
//        )
//    }
//
//    private data class ClassID(
//        val name: String,
//        val interfaces: List<String>,
//        val superClass: String?,
//        val fields: List<String>,
//        val methods: List<String>,
//        val instructions: List<Int>
//    )

//    public fun InputStream.parseNode(): ClassNode {
//        val node = ClassNode()
//        ClassReader(this).accept(node, 0)
//        return node
//    }
}