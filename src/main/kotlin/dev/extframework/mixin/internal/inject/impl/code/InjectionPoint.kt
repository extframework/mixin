package dev.extframework.mixin.internal.inject.impl.code

import dev.extframework.mixin.api.InstructionSelector
import dev.extframework.mixin.internal.inject.impl.code.InjectionPoint.Group
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

public interface InjectionPoint {
    public val placementId: Any

    public fun getPoints(
        source: InsnList,
    ): List<Group>

    public data class Group(
        val start: AbstractInsnNode,
        val end: AbstractInsnNode,
    )
}

public data class SingleInjectionPoint(
    public val selector: InstructionSelector,
    public val ordinal: Int,
    public val count: Int,
) : InjectionPoint {
    override val placementId: Any = selector

    override fun getPoints(
        source: InsnList,
    ): List<Group> {
        var select = selector.select(source)
            .map { Group(it, it) }

        return select
            .subList(ordinal, select.size)
            .take(count)
    }
}

public data class BlockInjectionPoint(
    public val start: InstructionSelector,
    public val end: InstructionSelector,
    public val ordinal: Int,
    public val count: Int,
) : InjectionPoint {
    override val placementId: Any = start to end

    override fun getPoints(
        source: InsnList,
    ): List<Group> {
        val ending = end.select(source)

        var select = start.select(source).map {
            val e = ending.first { e ->
                source.indexOf(e) > source.indexOf(it)
            }

            Group(it, e)
        }

        return select
            .subList(ordinal, select.size)
            .take(count)
    }
}