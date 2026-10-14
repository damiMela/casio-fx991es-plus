package com.damimela.fx991.engine

object MatrixOps {
    const val MAX_DIM = 4

    private fun isZero(v: Value) = v.isZeroValue() || (Ops.abs(v).d < 1e-13 && !(v is Real && v.isExact))

    fun transpose(m: MatV) = MatV.build(m.cols, m.rows) { r, c -> m[c, r] }

    fun square(m: MatV) {
        if (m.rows != m.cols) throw CalcError(ErrorKind.DIMENSION)
    }

    fun det(m: MatV): Value {
        square(m)
        val n = m.rows
        val a = Array(n) { r -> Array(n) { c -> m[r, c] } }
        var det: Value = Real.ONE
        for (col in 0 until n) {
            var piv = -1
            var best = 0.0
            for (r in col until n) {
                val mag = Ops.abs(a[r][col]).d
                if (mag > best && !(a[r][col].isZeroValue())) { best = mag; piv = r }
            }
            if (piv < 0 || best < 1e-13 && !(a[piv][col] is Real && (a[piv][col] as Real).isExact)) return Real.ZERO
            if (piv != col) { val t = a[piv]; a[piv] = a[col]; a[col] = t; det = Ops.neg(det) }
            det = Ops.mul(det, a[col][col])
            for (r in col + 1 until n) {
                if (a[r][col].isZeroValue()) continue
                val f = Ops.div(a[r][col], a[col][col])
                for (c in col until n) a[r][c] = Ops.sub(a[r][c], Ops.mul(f, a[col][c]))
            }
        }
        return det
    }

    fun inverse(m: MatV): MatV {
        square(m)
        val n = m.rows
        val a = Array(n) { r -> Array(2 * n) { c -> if (c < n) m[r, c] else if (c - n == r) Real.ONE else Real.ZERO } }
        for (col in 0 until n) {
            var piv = -1
            var best = 0.0
            for (r in col until n) {
                val mag = Ops.abs(a[r][col]).d
                if (mag > best && !a[r][col].isZeroValue()) { best = mag; piv = r }
            }
            val pv = if (piv >= 0) a[piv][col] else null
            if (pv == null || (best < 1e-13 && !(pv is Real && pv.isExact))) throw CalcError(ErrorKind.MATH)
            val t = a[piv]; a[piv] = a[col]; a[col] = t
            val p = a[col][col]
            for (c in 0 until 2 * n) a[col][c] = Ops.div(a[col][c], p)
            for (r in 0 until n) {
                if (r == col || a[r][col].isZeroValue()) continue
                val f = a[r][col]
                for (c in 0 until 2 * n) a[r][c] = Ops.sub(a[r][c], Ops.mul(f, a[col][c]))
            }
        }
        return MatV.build(n, n) { r, c -> a[r][n + c] }
    }

    /** Solves A x = b for square A; throws Math ERROR when there is no unique solution. */
    fun solve(a: MatV, b: List<Value>): List<Value> {
        val inv = inverse(a)
        return (0 until a.rows).map { r ->
            var s: Value = Real.ZERO
            for (c in 0 until a.cols) s = Ops.add(s, Ops.mul(inv[r, c], b[c]))
            s
        }
    }

    fun map(m: MatV, f: (Value) -> Value) = MatV(m.rows, m.cols, m.data.map(f))
}

object VectorOps {
    private fun check(a: VecV, b: VecV) { if (a.size != b.size) throw CalcError(ErrorKind.DIMENSION) }

    fun dot(a: VecV, b: VecV): Value {
        check(a, b)
        var s: Value = Real.ZERO
        for (i in 0 until a.size) s = Ops.add(s, Ops.mul(a.items[i], b.items[i]))
        return s
    }

    fun cross(a: VecV, b: VecV): VecV {
        check(a, b)
        fun x(u: VecV) = if (u.size == 2) VecV(u.items + Real.ZERO) else u
        if (a.size !in 2..3) throw CalcError(ErrorKind.DIMENSION)
        val p = x(a); val q = x(b)
        fun m(i: Int, j: Int) = Ops.sub(Ops.mul(p.items[i], q.items[j]), Ops.mul(p.items[j], q.items[i]))
        return VecV(listOf(m(1, 2), m(2, 0), m(0, 1)))
    }

    /** Angle between vectors in radians. */
    fun angleRad(a: VecV, b: VecV): Double {
        val d = dot(a, b).re().d
        val n = Ops.abs(a).d * Ops.abs(b).d
        if (n == 0.0) throw CalcError(ErrorKind.MATH)
        return Math.acos((d / n).coerceIn(-1.0, 1.0))
    }

    fun unit(a: VecV): VecV {
        val n = Ops.abs(a)
        if (n.isZero) throw CalcError(ErrorKind.MATH)
        return VecV(a.items.map { Ops.div(it, n) })
    }
}
