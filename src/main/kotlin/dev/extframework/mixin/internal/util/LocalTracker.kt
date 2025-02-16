package dev.extframework.mixin.internal.util

import dev.extframework.mixin.api.TypeSort

public class LocalTracker(
    initial: Int
) {
    public var current: Int = initial
        private set

    public fun increment(
        sort: TypeSort
    ) : Int {
        val old = current
        current = current + sort.size

        return old
    }
}