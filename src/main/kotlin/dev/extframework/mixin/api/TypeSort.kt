package dev.extframework.mixin.api

public enum class TypeSort(
    @JvmField
    public val offset: Int
) {
    INT(0),
    LONG(1),
    FLOAT(2),
    DOUBLE(3),
    OBJECT(4),
}