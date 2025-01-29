package dev.extframework.mixin.api

// In this stack all types that can confirm to an int (ie booleans, chars, shorts, etc.
// are represented by the Integer type.) This is due to the internal mechanisms of how the
// JVM works and that at runtime these types cannot be told apart.
public interface Stack {
    public fun iterator() : Iterator<Any?>

    public val size: Int

    public fun <T> get(index: Int): T

    public fun set(index: Int, value: Any?)
//    public fun <T> pop(): T
//
//    public fun <T : Any> push(value: T)
//
//    public fun <T> push(value: T, type: JvmType)

//    public data class Stacked(
//        @JvmField
//        val value: Any?,
//        @JvmField
//        val type: JvmType
//    )
}