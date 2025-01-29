package dev.extframework.mixin.internal.inject.impl.code

import dev.extframework.mixin.api.Captured
import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.api.MixinFlow
import dev.extframework.mixin.api.Stack

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
        val transformed = when (type) {
            TypeSort.INT -> {
                when (value) {
                    is Char -> value.code
                    is Boolean -> if (value) 1 else 0
                    is Short -> value.toInt()
                    is Byte -> value.toInt()
                    is Int -> value
                    else -> throw Exception(
                        "Cannot transform value from mixin result to an Int which was the requested type."
                    )
                }
            }

            TypeSort.LONG -> {
                when (value) {
                    is Long -> value
                    else -> throw Exception(
                        "Cannot transform value from mixin result to an Long which was the requested type."
                    )
                }
            }

            TypeSort.FLOAT -> {
                when (value) {
                    is Float -> value
                    else -> throw Exception(
                        "Cannot transform value from mixin result to an Float which was the requested type."
                    )
                }
            }

            TypeSort.DOUBLE -> {
                when (value) {
                    is Double -> value
                    else -> throw Exception(
                        "Cannot transform value from mixin result to an Double which was the requested type."
                    )
                }
            }

            TypeSort.OBJECT -> value
        }

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
        value = t
    }
}

public class InternalMixinStack(
    private val internal: Array<Any?>
) : Stack {
    override fun iterator(): Iterator<Any?> {
        return internal.iterator()
    }

    //    override fun iterator(): Iterator<Stacked> {
//        return internal.iterator()
//    }
//
//    override fun <T> pop(): T {
//       return internal.removeLast().value as T
//    }
//
//    override fun <T: Any> push(value: T) {
//        internal.add(Stacked(value, JvmType.OBJECT))
//    }
//
//    override fun <T> push(value: T, type: JvmType) {
//        internal.add(Stacked(value, type))
//    }
    override val size: Int = internal.size

    override fun <T> get(index: Int): T {
        return internal[index] as T
    }

    override fun set(index: Int, value: Any?) {
        check(index in 0 until size) { "Invalid size given for stack." }
        internal[index] = value
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