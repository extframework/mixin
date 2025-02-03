package dev.extframework.mixin.api


public enum class FieldAccessType(
    public val opcodes: Set<Int>
) {
    SET(setOf(Opcodes.PUTFIELD, Opcodes.PUTSTATIC)),
    GET(setOf(Opcodes.GETFIELD, Opcodes.GETSTATIC)),
    EITHER(SET.opcodes + GET.opcodes),
}