package dev.extframework.mixin.api

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class InjectCode(
    val value: String = "",
    val type: InjectionType = InjectionType.BEFORE,
    val point: Select = Select(
        defaulted = true
    ),
    val block: Array<Select> = [],

    // Locals are always 0 based despite method access
    val locals: IntArray = [],
    val ordinal: Int = 0,
    val count: Int = 1,
)
