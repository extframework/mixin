package dev.extframework.mixin.test.engine.analysis

class ControlFlowInTryCatch {
    fun thisIsATest() {
        try {
            println("")
        } catch(e: Exception) {
            println(e.message)
        } finally {
            println("finally")
        }
    }
}