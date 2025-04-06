package dev.extframework.mixin.engine.impl.code

import dev.extframework.mixin.api.Captured
import dev.extframework.mixin.api.MixinFlow
import dev.extframework.mixin.api.Stack
import dev.extframework.mixin.api.TypeSort

public class InternalMixinFlow : MixinFlow {
    override fun on(): MixinFlow.Result<*> {
        return MixinFlow.Result(false, null, null)
    }

    override fun yield(): MixinFlow.Result<Nothing> {
        return MixinFlow.Result<Nothing>(true, null, null)
    }

    override fun <T> yield(value: T): MixinFlow.Result<T> {
        return MixinFlow.Result(true, value, TypeSort.OBJECT)
    }

    override fun <T> yield(
        value: T,
        type: TypeSort
    ): MixinFlow.Result<T> {
        val transformed = transformType<T>(type, value)

        return MixinFlow.Result(true, transformed as T, type)
    }
}

public class InternalCaptured<T>(
    private var value: T,
    override val type: TypeSort
) : Captured<T> {
    override fun get(): T {
        return value
    }

    override fun set(t: T) {
        val transformed = transformType(type, t)

        value = transformed as T
    }
}

private fun <T> transformType(sort: TypeSort, value: T): Any? = when (sort) {
    TypeSort.INT -> {
        when (value) {
            is Char -> value.code
            is Boolean -> if (value) 1 else 0
            is Short -> value.toInt()
            is Byte -> value.toInt()
            is Int -> value
            else -> throw Exception(
                "Cannot transform value '$value' from mixin to an Int which was the requested type."
            )
        }
    }

    TypeSort.LONG -> {
        when (value) {
            is Long -> value
            else -> throw Exception(
                "Cannot transform value '$value' from mixin to an Long which was the requested type."
            )
        }
    }

    TypeSort.FLOAT -> {
        when (value) {
            is Float -> value
            else -> throw Exception(
                "Cannot transform value '$value' from mixin to an Float which was the requested type."
            )
        }
    }

    TypeSort.DOUBLE -> {
        when (value) {
            is Double -> value
            else -> throw Exception(
                "Cannot transform value '$value' from mixin to an Double which was the requested type."
            )
        }
    }

    TypeSort.OBJECT -> value
}

public class InternalMixinStack(
    private val internal: Array<Any?>
) : Stack {
    override fun iterator(): Iterator<Any?> {
        return internal.iterator()
    }

    override val size: Int = internal.size

    override fun <T> get(index: Int): T {
        return internal[index] as T
    }

    override fun set(index: Int, value: Any?) {
        check(index in 0 until size) { "Invalid size given for stack." }
        internal[index] = value
    }

    override fun toString(): String {
        return "Stack {${internal.joinToString(", ")}}"
    }
}

//public class InternalLocal<T>(
//    private var value: T
//) : Captured<T> {
//    override fun get(): T {
//        return value
//    }
//
//    override fun set(t: T) {
//        value = t
//    }
//}