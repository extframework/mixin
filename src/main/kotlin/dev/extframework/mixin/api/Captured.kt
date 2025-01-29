package dev.extframework.mixin.api

import kotlin.reflect.KProperty

public interface Captured<T> {
    public val type: TypeSort

    public fun get(): T

    public fun set(t: T)

    public operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): T {
        return get()
    }

    public operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T
    ) {
        set(value)
    }
}