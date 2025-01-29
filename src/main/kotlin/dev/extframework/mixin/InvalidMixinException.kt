package dev.extframework.mixin

import dev.extframework.mixin.api.ClassReference

public class InvalidMixinException(
    public val mixin: ClassReference,
    public val rootCause: MixinExceptionCause,
    vars: List<String> = listOf(),
    throwable: Throwable? = null
) : Exception("Mixin: '${mixin.name}' is invalid. ${rootCause.fillIn(vars)}", throwable) {
    public constructor(
        mixin: ClassReference,
        cause: MixinExceptionCause,
        vararg vars: String
    ) : this(mixin, cause, vars.toList())
}

public enum class MixinExceptionCause(
    public val message: String,
) {
    NoMixinAnnotation("Mixin class must be annotated with @Mixin."),
    NoTarget("Mixin annotation must define a target through annotation fields 'value' or 'applicator'."),
    ApplicatorInstantiation("Failed to instantiate custom mixin applicator: '{}'"),
    DoubleCodeInjectionPoints(
        "A code Injection cannot define both a single and " +
                "block injection point. Use only 'at' or 'block' in " +
                "your @InjectCode method. Method name: '{}'"
    ),
    InvalidCodeInjectionPointBlockSize("Block selection of the wrong size! Please define a beginning and end selector with @Select (one each)"),
    NoCodeInjectionPoints(
        "A code Injection must define either a single or " +
                "block injection point. Use only 'at' or 'block' in " +
                "your @InjectCode method. Method name: '{}'"
    ),
    CodeSelectorExclusivity("Selectors in @Select should be mutually exclusive. Only define one boundary, invocation, field access, opcode, or custom selector!"),
    NoCodeSelectorsDefined("No selectors defined in @Select. Please define one, either: boundary, invocation, field access, opcode, or custom."),
    NoCodeMethodFound("Failed to find a method with the name: '{}' to inject into in the target class: '{}'"),
    CodeMethodOverloadAmbiguity("Overload ambiguity! Instruction injection of method: '{}' matches more than one method: '{}'."),
    CodeInvalidMethodDescription("The given method description is invalid. It should follow the following format: '{}'.")
    ;

    public fun fillIn(
        vars: List<String>
    ): String {
        return vars.fold(message) { acc, it ->
            acc.replaceFirst("{}", it)
        }
    }
}