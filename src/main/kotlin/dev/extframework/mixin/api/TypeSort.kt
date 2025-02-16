package dev.extframework.mixin.api

public enum class TypeSort(
    @JvmField
    public val offset: Int,
    @JvmField
    public val size: Int
) {
    INT(0, 1),
    LONG(1, 2),
    FLOAT(2, 1),
    DOUBLE(3, 2),
    OBJECT(4, 1),
}