package dev.extframework.mixin

import dev.extframework.mixin.engine.tag.ClassTag

public class InvalidMixinException(
    public val mixin: ClassTag,
    public val rootCause: MixinExceptionCause,
    vars: List<String> = listOf(),
    throwable: Throwable? = null
) : Exception("Mixin: '$mixin' is invalid. ${rootCause.fillIn(vars)}", throwable) {
    public constructor(
        mixin: ClassTag,
        cause: MixinExceptionCause,
        vararg vars: Any
    ) : this(mixin, cause, vars.map { it.toString() })
}

public enum class MixinExceptionCause(
    public val message: String,
) {
    NoMixinAnnotation("Mixin class must be annotated with @Mixin."),
    NoTarget("Mixin annotation must define a target through annotation fields 'value' or 'targets'."),
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
    CodeSelectorExclusivity(
        "Selectors in @Select should be mutually exclusive. Only define one boundary, " +
                "invocation, field access, opcode, or custom selector!"
    ),
    NoCodeSelectorsDefined(
        "No selectors defined in @Select. Please define one, either: boundary, " +
                "invocation, field access, opcode, or custom."
    ),
    NoCodeMethodFound("Failed to find a method with the name: '{}' to inject into in the target class: '{}'"),
    CodeMethodOverloadAmbiguity(
        "Overload ambiguity! Instruction injection of method: '{}' matches more" +
                " than one method: '{}'."
    ),
    CodeInvalidMethodDescription(
        "The given method description is invalid. It should follow the following " +
                "format: '{}'."
    ),
    ConflictingCodeInjections(
        "At least one of the following attempts to overwrite an instruction region " +
                "that another code injection targets: '{}'."
    ),
    CodeShouldBeStatic("Your code injection should be static because the target method is static! Method: '{}'"),
    CodeWrongNumLocals(
        "Mixin: '{}' contains code injection: '{}' which requests " +
                "more locals than are present by its point of injection. The available locals are: '{}'. " +
                "This mixin requests a local of slot: '{}'."

    ),
    CodeWrongReturnType(
        "Mixin: '{}' contains code injection: '{}' and specifies a return type of '{}'. The return " +
                "type for a code injection method should either be 'void' or 'MixinFlow.Result'."
    ),
    CodeLocalAnnotationParameterMismatch(
        "Mixin: '{}' contains code injection: '{}' which defines a different " +
                "amount of locals to capture in its @InjectCode annotation than it does in its method signature. Expected to" +
                "find '{}' parameters matching: 'Captured<...>' but found '{}'"

    ),
    CodeWrongLocalType(
        "Mixin: '{}' contains code injection: '{}' which requests to capture a local" +
                "variable in slot: '{}' (if your mixin is non static, you've probably defined it as slot '{}') with type: " +
                "'{}' but the actual type in local slot '{}' is '{}'."

    ),
    CodeInvalidParameterType(
        "Mixin: '{}' contains code injection: '{}' which has parameter: '{}'. All parameter " +
                "types must either be a 'MixinFlow', 'Captured' type, or 'Stack' type."
    ),
    CodeFailedToMatchPoints("Failed to match {} point(s) of selector: '{}' in mixin '{}'."),
    CodeCannotApplyToInterfaces("Code injection '{}' is not allowed to apply to an interface.");

    public fun fillIn(
        vars: List<String>
    ): String {
        return vars.fold(message) { acc, it ->
            acc.replaceFirst("{}", it)
        }
    }
}