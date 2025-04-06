package dev.extframework.mixin.engine.generate

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.engine.operation.OperationData

public interface GenerationData : OperationData {
    public val name: ClassReference
}