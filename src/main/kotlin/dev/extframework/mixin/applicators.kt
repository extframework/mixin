package dev.extframework.mixin

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.MixinApplicator

public open class TargetedApplicator(
    public val target: ClassReference
) : MixinApplicator {
    override fun applies(
        ref: ClassReference,
    ): Boolean {
        return ref == target
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TargetedApplicator) return false

        if (target != other.target) return false

        return true
    }

    override fun hashCode(): Int {
        return target.hashCode()
    }
}

public object BroadApplicator : MixinApplicator {
    override fun applies(
        ref: ClassReference,
    ): Boolean = true
}