package dev.extframework.mixin.test

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import kotlin.reflect.KClass

private class THIS

fun classNode(
    cls: KClass<*>
): ClassNode {
    val type = Type.getType(cls.java)
    val stream = THIS::class.java.getResourceAsStream("/${type.internalName}.class")
    val node = ClassNode()
    ClassReader(stream).accept(node, 0)
    return node
}

fun load(node: ClassNode) : Class<*> {
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    node.accept(writer)
    val bytes = writer.toByteArray()

    val nodeName = node.name.replace("/", ".")

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