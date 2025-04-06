package dev.extframework.mixin.engine.tag

import dev.extframework.mixin.api.ClassReference

public data class BasicTag(
    override val reference: ClassReference,
) : ClassTag {
    override fun toString(): String {
        return reference.toString()
    }
}