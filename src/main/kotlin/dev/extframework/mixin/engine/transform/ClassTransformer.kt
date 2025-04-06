package dev.extframework.mixin.engine.transform

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.engine.operation.OperationData
import dev.extframework.mixin.engine.operation.OperationParent
import dev.extframework.mixin.engine.operation.OperationRegistry
import org.objectweb.asm.tree.ClassNode

public interface ClassTransformer<T: OperationData> {
    public val registry: OperationRegistry<T>
    public val parents: Set<OperationParent>

    @Throws(InvalidMixinException::class)
    public fun transform(
        node: ClassNode
    ) : ClassNode
}