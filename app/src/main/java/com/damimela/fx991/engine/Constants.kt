package com.damimela.fx991.engine

class PhysConst(val symbol: String, val name: String, val value: Double)

class Conversion(val label: String, val factor: Double, val offset: Double = 0.0) {
    fun apply(x: Double) = x * factor + offset
}

object Constants {
    /** The 40 scientific constants of the fx-991ES PLUS CONST menu (CODATA 2010). */
    val CONSTS = listOf(
        PhysConst("mp", "Proton mass", 1.672621777e-27),
        PhysConst("mn", "Neutron mass", 1.674927351e-27),
        PhysConst("me", "Electron mass", 9.10938291e-31),
        PhysConst("mμ", "Muon mass", 1.883531475e-28),
        PhysConst("a0", "Bohr radius", 5.2917721092e-11),
        PhysConst("h", "Planck constant", 6.62606957e-34),
        PhysConst("μN", "Nuclear magneton", 5.05078353e-27),
        PhysConst("μB", "Bohr magneton", 9.27400968e-24),
        PhysConst("ħ", "Planck constant, rationalized", 1.054571726e-34),
        PhysConst("α", "Fine-structure constant", 7.2973525698e-3),
        PhysConst("re", "Classical electron radius", 2.8179403267e-15),
        PhysConst("λc", "Compton wavelength", 2.4263102389e-12),
        PhysConst("γp", "Proton gyromagnetic ratio", 2.675222005e8),
        PhysConst("λcp", "Proton Compton wavelength", 1.32140985623e-15),
        PhysConst("λcn", "Neutron Compton wavelength", 1.31959090468e-15),
        PhysConst("R∞", "Rydberg constant", 10973731.568539),
        PhysConst("u", "Atomic mass constant", 1.660538921e-27),
        PhysConst("μp", "Proton magnetic moment", 1.410606743e-26),
        PhysConst("μe", "Electron magnetic moment", -9.28476430e-24),
        PhysConst("μn", "Neutron magnetic moment", -9.6623647e-27),
        PhysConst("μμ", "Muon magnetic moment", -4.49044807e-26),
        PhysConst("F", "Faraday constant", 96485.3365),
        PhysConst("e", "Elementary charge", 1.602176565e-19),
        PhysConst("NA", "Avogadro constant", 6.02214129e23),
        PhysConst("k", "Boltzmann constant", 1.3806488e-23),
        PhysConst("Vm", "Molar volume of ideal gas", 0.022413968),
        PhysConst("R", "Molar gas constant", 8.3144621),
        PhysConst("C0", "Speed of light in vacuum", 299792458.0),
        PhysConst("C1", "First radiation constant", 3.74177153e-16),
        PhysConst("C2", "Second radiation constant", 0.01438777),
        PhysConst("σ", "Stefan-Boltzmann constant", 5.670373e-8),
        PhysConst("ε0", "Electric constant", 8.854187817e-12),
        PhysConst("μ0", "Magnetic constant", 1.2566370614e-6),
        PhysConst("Φ0", "Magnetic flux quantum", 2.067833758e-15),
        PhysConst("g", "Standard gravity", 9.80665),
        PhysConst("G0", "Conductance quantum", 7.7480917346e-5),
        PhysConst("Z0", "Characteristic impedance of vacuum", 376.730313461),
        PhysConst("t", "Celsius temperature", 273.15),
        PhysConst("G", "Newtonian constant of gravitation", 6.67384e-11),
        PhysConst("atm", "Standard atmosphere", 101325.0)
    )

    /** The 40 unit conversions of the CONV menu. */
    val CONVERSIONS = listOf(
        Conversion("in▶cm", 2.54), Conversion("cm▶in", 1 / 2.54),
        Conversion("ft▶m", 0.3048), Conversion("m▶ft", 1 / 0.3048),
        Conversion("yd▶m", 0.9144), Conversion("m▶yd", 1 / 0.9144),
        Conversion("mile▶km", 1.609344), Conversion("km▶mile", 1 / 1.609344),
        Conversion("n mile▶m", 1852.0), Conversion("m▶n mile", 1 / 1852.0),
        Conversion("acre▶m²", 4046.8564224), Conversion("m²▶acre", 1 / 4046.8564224),
        Conversion("gal(US)▶ℓ", 3.785411784), Conversion("ℓ▶gal(US)", 1 / 3.785411784),
        Conversion("gal(UK)▶ℓ", 4.54609), Conversion("ℓ▶gal(UK)", 1 / 4.54609),
        Conversion("pc▶km", 3.08567782e13), Conversion("km▶pc", 1 / 3.08567782e13),
        Conversion("km/h▶m/s", 1 / 3.6), Conversion("m/s▶km/h", 3.6),
        Conversion("oz▶g", 28.349523125), Conversion("g▶oz", 1 / 28.349523125),
        Conversion("lb▶kg", 0.45359237), Conversion("kg▶lb", 1 / 0.45359237),
        Conversion("atm▶Pa", 101325.0), Conversion("Pa▶atm", 1 / 101325.0),
        Conversion("mmHg▶Pa", 133.322387415), Conversion("Pa▶mmHg", 1 / 133.322387415),
        Conversion("hp▶kW", 0.745699872), Conversion("kW▶hp", 1 / 0.745699872),
        Conversion("kgf/cm²▶Pa", 98066.5), Conversion("Pa▶kgf/cm²", 1 / 98066.5),
        Conversion("kgf·m▶J", 9.80665), Conversion("J▶kgf·m", 1 / 9.80665),
        Conversion("lbf/in²▶kPa", 6.894757293), Conversion("kPa▶lbf/in²", 1 / 6.894757293),
        Conversion("°F▶°C", 1 / 1.8, -32 / 1.8), Conversion("°C▶°F", 1.8, 32.0),
        Conversion("J▶cal", 1 / 4.1868), Conversion("cal▶J", 4.1868)
    )

    fun const(n: Int): Real {
        val c = CONSTS.getOrNull(n - 1) ?: throw CalcError(ErrorKind.ARGUMENT)
        return r15(c.value)
    }

    fun convert(n: Int, x: Value): Value {
        val c = CONVERSIONS.getOrNull(n - 1) ?: throw CalcError(ErrorKind.ARGUMENT)
        val r = x as? Real ?: throw CalcError(ErrorKind.MATH)
        return r15(c.apply(r.d))
    }
}
