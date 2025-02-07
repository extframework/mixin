package dev.extframework.mixin.internal.util

public class LocalTracker(
    initial: Int
) {
    public var current: Int = initial
        private set

    public fun increment() : Int {
        return ++current
    }
}