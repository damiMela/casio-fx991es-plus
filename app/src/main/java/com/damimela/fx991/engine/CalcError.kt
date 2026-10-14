package com.damimela.fx991.engine

enum class ErrorKind(val title: String) {
    MATH("Math ERROR"),
    SYNTAX("Syntax ERROR"),
    STACK("Stack ERROR"),
    ARGUMENT("Argument ERROR"),
    DIMENSION("Dimension ERROR"),
    VARIABLE("Variable ERROR")
}

/** Calculator error. [pos] is the character offset in the source expression where the problem was detected (or -1). */
class CalcError(val kind: ErrorKind, val pos: Int = -1) : RuntimeException(kind.title) {
    fun at(p: Int) = if (pos >= 0) this else CalcError(kind, p)
}
