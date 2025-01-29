import dev.extframework.mixin.api.Captured
import dev.extframework.mixin.api.Custom
import dev.extframework.mixin.api.Field
import dev.extframework.mixin.api.FieldAccessType
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.InjectionBoundary
import dev.extframework.mixin.api.InjectionBoundary.TAIL
import dev.extframework.mixin.api.InjectionSelector
import dev.extframework.mixin.api.Invoke
import dev.extframework.mixin.api.LDC
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.api.MixinFlow
import dev.extframework.mixin.api.Opcode
import dev.extframework.mixin.api.Select
import dev.extframework.mixin.api.Stack
import javax.tools.ToolProvider

@Mixin(
    TestMixin::class,
)
class TestMixin {
    @InjectCode(
        at = Select(
            InjectionBoundary.HEAD,
            ordinal = 0,
            invoke = Invoke(
                TestMixin::class,
                method = "method",
            ),
            field = Field(
                Field::class,
                "field",
                type = FieldAccessType.GET
            ),
            opcode = Opcode(
                50,
                ldc = LDC("Some value")
            ),
        ),
        locals = [1, 2]
    )
    fun method(
        arg1: String,
        arg2: Int,
        arg3: Captured<Int>,
        arg4: Stack,
        flow: MixinFlow,
    ): MixinFlow.Result<*> {
        flow.yield(1)

        return flow.on()
    }
}