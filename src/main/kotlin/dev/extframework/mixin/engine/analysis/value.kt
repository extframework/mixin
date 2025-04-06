package dev.extframework.mixin.engine.analysis

import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.api.TypeSort.DOUBLE
import dev.extframework.mixin.api.TypeSort.FLOAT
import dev.extframework.mixin.api.TypeSort.INT
import dev.extframework.mixin.api.TypeSort.LONG
import dev.extframework.mixin.api.TypeSort.OBJECT
import org.objectweb.asm.Label
import org.objectweb.asm.Type

public sealed interface JvmValueRef {
    public val sort: TypeSort
}

public data class SortValueRef(
    override val sort: TypeSort
) : JvmValueRef

public data class UninitializedObjectRef(
    val objectType: Type,
    val label: Label,
) : JvmValueRef {
    override val sort: TypeSort = OBJECT
}

public data class ObjectValueRef(
    val objectType: Type
) : JvmValueRef {
    override val sort: TypeSort = OBJECT
}

public object NullValueRef : JvmValueRef {
    override val sort: TypeSort = OBJECT
}

internal fun Type.toValueRef(): JvmValueRef {
    return when (sort) {
        Type.INT, Type.SHORT, Type.BYTE, Type.CHAR, Type.BOOLEAN -> SortValueRef(INT)
        Type.FLOAT -> SortValueRef(FLOAT)
        Type.LONG -> SortValueRef(LONG)
        Type.DOUBLE -> SortValueRef(DOUBLE)
        Type.ARRAY, Type.OBJECT -> ObjectValueRef(this)
        else -> throw Exception("Unsupported type $this")
    }
}