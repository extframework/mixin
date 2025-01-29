package dev.extframework.mixin.api

import org.objectweb.asm.tree.ClassNode

public fun interface MixinApplicator {
    public fun applies(ref: ClassReference, node: ClassNode): Boolean
}