package dev.extframework.mixin.internal.analysis

import dev.extframework.mixin.api.TypeSort

internal sealed interface Certainty {
    val isSure: Boolean

    data class Sure(
        val type: TypeSort
    ) : Certainty {
        override val isSure: Boolean = true
    }

    data class Unsure(
        val possibilities: List<TypeSort>
    ) : Certainty {
        constructor(vararg possibilities: TypeSort) : this(possibilities.toList())

        override val isSure: Boolean = false
    }
}
