package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.fail

val DEFAULT_SETTINGS = DisplaySettings()

fun newCtx(angle: AngleUnit = AngleUnit.DEG, complex: Boolean = false) = Context().also { it.angle = angle; it.complex = complex }

/** Evaluates [src] and formats the result as plain text (natural display flattened). */
fun calc(src: String, ctx: Context = newCtx(), settings: DisplaySettings = DEFAULT_SETTINGS, exact: Boolean = true): String =
    Formatter.text(Engine.evaluate(src, ctx).value, settings, exact)

fun dec(src: String, ctx: Context = newCtx()): String = calc(src, ctx, exact = false)

fun assertError(kind: ErrorKind, src: String, ctx: Context = newCtx()) {
    try {
        Engine.evaluate(src, ctx)
        fail("expected ${kind.title} for '$src'")
    } catch (e: CalcError) {
        assertEquals("for '$src'", kind, e.kind)
    }
}

fun num(src: String, ctx: Context = newCtx()): Double = (Engine.evaluate(src, ctx).value as Real).d
