package dev.extframework.mixin.api

// In this stack all types that can confirm to an int (ie booleans, chars, shorts, etc.
// are represented by the Integer type.) This is due to the internal mechanisms of how the
// JVM works and that at runtime these types cannot be told apart.
public interface Stack {
    public fun iterator() : Iterator<Any?>

    public val size: Int

    public fun <T> get(index: Int): T

    public fun set(index: Int, value: Any?)

    public fun <T> replaceLast(value: T) : T {
        if (size == 0) throw NoSuchElementException("Stack is empty.")

        val old = get<T>(size - 1)
        set(size - 1, value)

        return old
    }

    // Gets the index relative to the top, 0 is the top, 1 is the first down, etc.
    public fun <T> getRelative(index: Int): T {
        return get(size - 1 - index)
    }

    // Gets the index relative to the top, 0 is the top, 1 is the first down, etc.
    public fun setRelative(index: Int, value: Any?) {
        return set(size - 1 - index, value)
    }
}

