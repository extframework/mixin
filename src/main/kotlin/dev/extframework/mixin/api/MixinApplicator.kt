package dev.extframework.mixin.api

public fun interface MixinApplicator {
    public fun applies(ref: ClassReference): Boolean
}