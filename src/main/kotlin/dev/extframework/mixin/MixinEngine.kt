package dev.extframework.mixin

import dev.extframework.mixin.annotation.AnnotationProcessor
import dev.extframework.mixin.annotation.AnnotationTarget
import dev.extframework.mixin.annotation.impl.AnnotationProcessorImpl
import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.ClassReference.Companion.ref
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.InstructionSelector
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.engine.*
import dev.extframework.mixin.engine.generate.ClassGenerator
import dev.extframework.mixin.engine.impl.code.InstructionInjectionParser
import dev.extframework.mixin.engine.impl.code.InstructionInjector
import dev.extframework.mixin.engine.impl.method.MethodInjector
import dev.extframework.mixin.engine.operation.OperationData
import dev.extframework.mixin.engine.operation.OperationRegistry
import dev.extframework.mixin.engine.tag.BasicTag
import dev.extframework.mixin.engine.tag.ClassTag
import dev.extframework.mixin.engine.transform.ClassTransformer
import dev.extframework.mixin.engine.util.SelfOrderingTransformerList
import dev.extframework.mixin.engine.util.descriptor
import dev.extframework.mixin.engine.util.value
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

/** The plan:
 *
 * ROUND 1:
 *
 * MixinInjectors: Manage own injections and decide what applies to what class:
 *  - Injectors still registered by their respective annotations
 *  - All injections are registered with 'targets'
 *  - One instance of each injector, all nodes passed into transform pass through all injectors
 *  - All injectors define their own instance of 'MixinRegistry' which holds references from all
 *    data to its owning class:
 *  - No mixin helper
 *  - interface MixinRegistry
 *    - Register applied: Registers data w/ the defining class
 *    - Unregister: Unregisters all data based on the defining class
 *    - Implementations:
 *      - By target class (Code/Method): Data to be applied to one or more target class(es)
 *      - Broad: All data applied to every target class
 *  - Unload mixin: Removes data from registry of all injectors
 *
 * Reflection:
 *  - Only Registries keep track of registered data, there is no register method
 *      on a MixinInjector interface.
 *  - To declare a new injection on another injector, an injector should implement a registry
 *      that invokes registration upon self registration.
 *  - To declare a new injection on another injection that requires ordering, an injector
 *       should return an `Injection` that declares parents.
 *
 *  ROUND 2:
 *   - ProcessRequest - Request to transform an existing, or generate a new class
 *   - A Mixin Injector (renamed to processor) handles process requests
 *   - Per set of injections for a target, only 1 injection can generate the class,
 *     and it must come first in the set of injections to apply.
 *   - 2 generations should trigger an exception to be thrown.
 *   - Injection parents should be based on the type of MixinInjector, not the type of
 *       Injection. This solves multiple issues:
 *       - Optimization: Preprocess is not called on 1 injector more than once, and it is always
 *          done by the MixinEngine
 *       - Quazi-ordering in which an injection unfairly modifies the provided node before calling
 *          preprocess on the subsequent injector leading to a false reliance on preprocessing ordering.
 *          (this ordering should only be relied on during the actual process phase)
 *       - Duplicate injector types in the same MixinEngine with duplicate registries.
 *       - Duplicate Injection objects being created, thus duplicate injections inserted into classes.
 *   - The injector package to be moved out of /engine and into `operate`
 *     - `Injection` to be renamed to ClassOperation (WIP)
 *     - `InjectionData` to be renamed to ClassOperationData
 *     - `MixinInjector` to be renamed to `ClassProcessor`
 *     - `MixinRegistry` -> `OperationRegistry`
 *  - While a `mixin` is still defined with the @Mixin annotation, and this package is still
 *    called mixin, what used to be a MixinInjector does not necessarily perform a `Mixin injection`.
 *    Instead, you chain together a series of class operations to perform a mixin.
 *
 *
 *  Goals: This project is a library for manipulating the bytecode of target classes through
 *         an easily understandable annotation API. While a large goal of this project is to
 *         simply provide easy code injection, that aso necessitates the ability to generate
 *         new classes on the fly to make the API as easily understand as possible; this is
 *         reflected in the ClassTransformer and the ClassGenerator interfaces respectively.
 *         And finally, the last goal of this project is to be reloadable, meaning that it can
 *         produce reloadable class bytecode that allows new mixins to be registered and applied
 *         at anypoint during the execution of a program: this feature should and does not
 *         detract from the overall readability or ease of the API.
 */

public open class MixinEngine(
    private val typeProvider: (ClassReference) -> Class<*>? = {
        Class.forName(it.name)
    },
    public open val parsers: MutableMap<Type, InjectionParser<*>> = HashMap(),
    public open val generators: MutableList<ClassGenerator<*>> = ArrayList(),
    public open val transformers: SelfOrderingTransformerList = SelfOrderingTransformerList()
) {
    // Mixin class tag to its side effects
    private val sideEffects = HashMap<ClassTag, Set<ClassReference>>()
    protected open val annotationProcessor: AnnotationProcessor = AnnotationProcessorImpl {
        typeProvider(ClassReference(it)) as? Class<out Annotation> ?: throw ClassNotFoundException(it)
    }

    public constructor(
        redefinitionFlags: RedefinitionFlags,
        typeProvider: (ClassReference) -> Class<*>? = {
            Class.forName(it.name)
        },
    ) : this(typeProvider, HashMap(), ArrayList(), SelfOrderingTransformerList()) {
        val methodInjector = MethodInjector(redefinitionFlags)
        val instructionInjector = InstructionInjector(methodInjector)

        parsers[Type.getType(InjectCode::class.java)] = InstructionInjectionParser(instructionInjector) {
            typeProvider(ClassReference(it)) as Class<out InstructionSelector>
        }
        transformers.add(methodInjector)
        transformers.add(instructionInjector)
    }

    public open fun registerMixin(
        node: ClassNode
    ): Set<ClassReference> {
        return registerMixin(BasicTag(node.ref()), node)
    }

    public open fun registerMixin(
        tag: ClassTag,
        node: ClassNode
    ): Set<ClassReference> {
        val allAnnotations = annotationProcessor.processAll(
            node
        )

        val mixinAnnotation = allAnnotations
            .asSequence()
            .filter { it.target.elementType == AnnotationTarget.ElementType.CLASS }
            .find {
                it.annotation.desc == Mixin::class.descriptor
            }?.annotation ?: throw InvalidMixinException(tag, MixinExceptionCause.NoMixinAnnotation)

        fun invalidMixinAnno() {
            throw InvalidMixinException(
                tag,
                MixinExceptionCause.NoTarget
            )
        }

        if (mixinAnnotation.values == null) invalidMixinAnno()

        val targets = (setOfNotNull(
            mixinAnnotation.value<Type>("value")
        ) + (mixinAnnotation.value<Array<Type>>("targets")?.toList() ?: setOf()))
            .mapTo(HashSet(), ::ClassReference)
        if (targets.isEmpty()) invalidMixinAnno()

        val delta = allAnnotations.flatMapTo(HashSet()) {
            val parser = parsers[Type.getType(it.annotation.desc)] as? InjectionParser<OperationData>
                ?: return@flatMapTo emptySet()

            val output = parser.parse(
                tag,
                it.target,
                it.annotation,
                targets
            )

            (transformers.toSet().checkRegistration(
                parser.transformer
            ).registry as OperationRegistry<OperationData>).register(
                output.data,
                tag,
            )

            output.delta
        }

        sideEffects[tag] = delta

        return delta
    }

    public fun unregisterMixin(tag: ClassTag) : Set<ClassReference> {
        for (transformer in transformers) {
            transformer.registry.unregister(tag)
        }
        for (generator in generators) {
            generator.registry.unregister(tag)
        }
        return sideEffects[tag] ?: emptySet()
    }

    public open fun transform(
        node: ClassNode
    ): ClassNode {
        val processed = transformers
            .fold(node) { acc, transformer ->
                transformer.transform(acc)
            }

        return processed
    }

    public open fun generate(
        reference: ClassReference
    ): ClassNode? {
        val applicable = generators.filter {
            it.registry.canGenerate(reference)
        }

        if (applicable.isEmpty()) return null
        if (applicable.size > 1) throw OperationRegistrationException(
            "Only 1 generator should be able to generate a class at one time. Generators: '${applicable.joinToString(", ")}' all attempt to generate '$reference'."
        )

        val generator = applicable.first()

        val generated = generator.generate(reference)

        return transform(generated)
    }


    public companion object {
        internal fun Set<ClassTransformer<*>>.checkRegistration(
            item: ClassTransformer<*>,
        ): ClassTransformer<*> {
            if (!contains(item)) {
                throw OperationRegistrationException("The transformer: '$item' is not registered.")
            }

            return item
        }
    }
}