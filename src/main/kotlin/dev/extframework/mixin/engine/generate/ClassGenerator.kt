package dev.extframework.mixin.engine.generate

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.engine.operation.OperationParent
import org.objectweb.asm.tree.ClassNode

public interface ClassGenerator<T: GenerationData> {
    public val registry: GenerationRegistry<T>
    // TODO Remove
    public val parents: Set<OperationParent>

    // The reason that there is no preprocess on this method, is because
    // all generation data should be valid by default and so this method
    // should never throw any errors.
    @Throws(InvalidMixinException::class)
    public fun generate(
        ref: ClassReference
    ) : ClassNode
}