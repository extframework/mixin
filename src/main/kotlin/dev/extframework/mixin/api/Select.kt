package dev.extframework.mixin.api

// Each value here besides ordinal (and defaulted which isnt to be modified)
// are mutually exclusive with each other.
public annotation class Select(
    val value: InjectionBoundary = InjectionBoundary.IGNORE,
    val invoke: Invoke = Invoke(
        Nothing::class,
        "",
        defaulted = true
    ),
    val field: Field = Field(
        Nothing::class,
        "",
        defaulted = true
    ),
    val opcode : Opcode = Opcode(
        -1,
        defaulted = true
    ),
    val custom : Custom = Custom(
        Nothing::class,
        defaulted = true
    ),
    val ordinal: Int = 0,
    val defaulted: Boolean = false,
)
