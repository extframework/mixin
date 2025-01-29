package dev.extframework.mixin

import org.objectweb.asm.tree.ClassNode

public interface ClassTransformer {
    public fun transform(name: String, node: ClassNode): ClassNode
}