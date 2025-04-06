package dev.extframework.mixin.engine.generate

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.engine.operation.OperationRegistry

public interface GenerationRegistry<T: GenerationData> : OperationRegistry<T> {
    public fun canGenerate(name: ClassReference) : Boolean
}