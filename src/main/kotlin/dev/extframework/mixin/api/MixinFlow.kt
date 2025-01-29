package dev.extframework.mixin.api

public interface MixinFlow {
    public fun on(): Result<*>

    public fun yield(): Result<Nothing>

    public fun <T> yield(value: T): Result<T>

    public fun <T> yield(value: T, type: TypeSort): Result<T>

    public data class Result<T>(
//        @get:JvmName("yielding")
        @JvmField
        val yielding: Boolean,
//        @get:JvmName("value")
        @JvmField
        val value: T?,
//        @get:JvmName("type")
        @JvmField
        val type: TypeSort?
    ) {
        public fun fold(other: Result<T>): Result<T> {
            return if (value == null) other
            else this
        }
    }
}