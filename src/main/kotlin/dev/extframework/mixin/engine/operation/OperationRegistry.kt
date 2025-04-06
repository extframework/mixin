package dev.extframework.mixin.engine.operation

import dev.extframework.mixin.engine.tag.ClassTag

public interface OperationRegistry<T: OperationData> {
    public fun register(
        data: T,
        tag: ClassTag
    )

    public fun unregister(
        tag: ClassTag
    ) : List<T>
}