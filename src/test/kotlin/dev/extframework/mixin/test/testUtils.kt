package dev.extframework.mixin.test

import dev.extframework.common.util.make
import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.engine.tag.ClassTag
import dev.extframework.mixin.test.engine.analysis.CodeFlowTests
import dev.extframework.mixin.test.engine.inject.impl.code.TestCapturing.Dest
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import java.io.InputStream
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation
import kotlin.io.path.Path
import kotlin.io.path.writeBytes
import kotlin.reflect.KClass

private class THIS

fun classNode(
    cls: KClass<*>
): ClassNode {
    val type = Type.getType(cls.java)
    val stream = THIS::class.java.getResourceAsStream("/${type.internalName}.class")
    val node = ClassNode()
    ClassReader(stream).accept(node, ClassReader.EXPAND_FRAMES)
    return node
}

fun Instrumentation.reload(
    cls: Class<*>,
    node: ClassNode
) {
    check(isRedefineClassesSupported)

    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    node.accept(writer)
    val bytes = writer.toByteArray()

    val nodeName = node.name.replace("/", ".")

    val name = nodeName.substringAfterLast(".").replace("$", "_")

    Path("class-output/redefined/$name.class").apply { make() }.writeBytes(bytes)

    redefineClasses(
        ClassDefinition(
            cls,
            bytes
        )
    )
}

class TrackingMixinEngine() : MixinEngine() {
    val tracked: MutableSet<ClassReference> = HashSet<ClassReference>()

    override fun registerMixin(tag: ClassTag, node: ClassNode): Set<ClassReference> {
        val delta = super.registerMixin(tag, node)
        tracked.addAll(delta)
        return delta
    }
}

fun MixinEngine.load(original: KClass<*>): Class<*> {
//    val transformed = transform(
//        classNode(
//            original
//        )
//    )

    val cl = object : ClassLoader(getSystemClassLoader().parent) {
        override fun findClass(name: String): Class<*> {
            findLoadedClass(name)?.let { return it }

            val stream = THIS::class.java.getResourceAsStream("/${name.replace('.', '/')}.class")
            val node = ClassNode()
            ClassReader(stream).accept(node, ClassReader.EXPAND_FRAMES)

           val transformed=  transform(node)
            stripKotlinMetadata(transformed)

            val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            transformed.accept(writer)
            val bytes = writer.toByteArray()

            val pathName = name.substringAfterLast(".").replace("$", "_")

            Path("class-output/$pathName.class").apply { make() }.writeBytes(bytes)

            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    return cl.loadClass(original.java.name)
}

fun load(node: ClassNode): Class<*> {
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    node.accept(writer)
    val bytes = writer.toByteArray()

    val nodeName = node.name.replace("/", ".")

    val name = nodeName.substringAfterLast(".").replace("$", "_")

    Path("class-output/$name.class").apply { make() }.writeBytes(bytes)

    val cl = object : ClassLoader() {
        override fun loadClass(name: String): Class<*> {
            findLoadedClass(name)?.let { return it }

            return if (name == nodeName) {
                return defineClass(name, bytes, 0, bytes.size)
            } else super.loadClass(name)
        }
    }

    return cl.loadClass(nodeName)
}

fun stripKotlinMetadata(node: ClassNode) {
    node.visibleAnnotations = node.visibleAnnotations?.filterNot {
        it.desc == "Lkotlin/Metadata;" || it.desc == "Lkotlin/jvm/internal./ourceDebugExtension;"
    }
    node.invisibleAnnotations = node.invisibleAnnotations?.filterNot {
        it.desc == "Lkotlin/Metadata;" || it.desc == "Lkotlin/jvm/internal/SourceDebugExtension;"
    }
}

fun insnFor(
    stream: InputStream,
    method: String,
): InsnList {
    return methodFor(stream, method).instructions
}

fun insnFor(
    cls: KClass<*>,
    method: String,
): InsnList {
    val stream = CodeFlowTests::class.java.getResourceAsStream("/${cls.java.name.replace('.', '/')}.class")!!

    return insnFor(stream, method)
}

fun methodFor(
    cls: KClass<*>,
    method: String,
): MethodNode {
    val stream = CodeFlowTests::class.java.getResourceAsStream("/${cls.java.name.replace('.', '/')}.class")!!

    return methodFor(stream, method)
}

fun methodFor(
    stream: InputStream,
    method: String,
): MethodNode {
    val reader = ClassReader(stream)
    val node = ClassNode()
    reader.accept(node, 0)

    return node.methods.find {
        it.name == method
    }!!
}