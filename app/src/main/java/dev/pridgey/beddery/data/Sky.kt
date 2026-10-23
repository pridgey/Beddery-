package dev.pridgey.beddery.data

import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where things are in the sky, accurate to a fraction of a degree: plenty for a bedside chart.
 * Planet orbits use Paul Schlyter's simplified elements ("How to compute planetary positions").
 */
object Sky {
    private const val RAD = PI / 180
    private const val DEG = 180 / PI

    data class Equatorial(val raDeg: Double, val decDeg: Double)
    data class Horizontal(val altDeg: Double, val azDeg: Double)

    enum class Planet(val label: String) { MERCURY("Mercury"), VENUS("Venus"), MARS("Mars"), JUPITER("Jupiter"), SATURN("Saturn") }

    /** Days since 1999-12-31 00:00 UT, the epoch of the elements below. */
    fun dayNumber(instant: Instant): Double = instant.toEpochMilli() / 86_400_000.0 + 2440587.5 - 2451543.5

    private fun rev(x: Double) = ((x % 360) + 360) % 360

    private class Elements(val n: Double, val i: Double, val w: Double, val a: Double, val e: Double, val m: Double)

    private fun elements(p: Planet, d: Double) = when (p) {
        Planet.MERCURY -> Elements(48.3313 + 3.24587e-5 * d, 7.0047 + 5.00e-8 * d, 29.1241 + 1.01444e-5 * d, 0.387098, 0.205635 + 5.59e-10 * d, 168.6562 + 4.0923344368 * d)
        Planet.VENUS -> Elements(76.6799 + 2.46590e-5 * d, 3.3946 + 2.75e-8 * d, 54.8910 + 1.38374e-5 * d, 0.723330, 0.006773 - 1.302e-9 * d, 48.0052 + 1.6021302244 * d)
        Planet.MARS -> Elements(49.5574 + 2.11081e-5 * d, 1.8497 - 1.78e-8 * d, 286.5016 + 2.92961e-5 * d, 1.523688, 0.093405 + 2.516e-9 * d, 18.6021 + 0.5240207766 * d)
        Planet.JUPITER -> Elements(100.4542 + 2.76854e-5 * d, 1.3030 - 1.557e-7 * d, 273.8777 + 1.64505e-5 * d, 5.20256, 0.048498 + 4.469e-9 * d, 19.8950 + 0.0830853001 * d)
        Planet.SATURN -> Elements(113.6634 + 2.38980e-5 * d, 2.4886 - 1.081e-7 * d, 339.3939 + 2.97661e-5 * d, 9.55475, 0.055546 - 9.499e-9 * d, 316.9670 + 0.0334442282 * d)
    }

    private fun eccentricAnomaly(mDeg: Double, e: Double): Double {
        val m = rev(mDeg) * RAD
        var ea = m + e * sin(m) * (1 + e * cos(m))
        repeat(6) { ea -= (ea - e * sin(ea) - m) / (1 - e * cos(ea)) }
        return ea
    }

    private fun obliquity(d: Double) = (23.4393 - 3.563e-7 * d) * RAD

    private class SunState(val x: Double, val y: Double, val lon: Double, val meanLon: Double, val meanAnomaly: Double)

    private fun sun(d: Double): SunState {
        val w = 282.9404 + 4.70935e-5 * d
        val e = 0.016709 - 1.151e-9 * d
        val m = 356.0470 + 0.9856002585 * d
        val ea = eccentricAnomaly(m, e)
        val xv = cos(ea) - e
        val yv = sqrt(1 - e * e) * sin(ea)
        val v = atan2(yv, xv) * DEG
        val r = sqrt(xv * xv + yv * yv)
        val lon = rev(v + w)
        return SunState(r * cos(lon * RAD), r * sin(lon * RAD), lon, rev(w + m), rev(m))
    }

    private fun toEquatorial(x: Double, y: Double, z: Double, d: Double): Equatorial {
        val ecl = obliquity(d)
        val ye = y * cos(ecl) - z * sin(ecl)
        val ze = y * sin(ecl) + z * cos(ecl)
        return Equatorial(rev(atan2(ye, x) * DEG), atan2(ze, sqrt(x * x + ye * ye)) * DEG)
    }

    fun sunPosition(instant: Instant): Equatorial {
        val d = dayNumber(instant)
        val s = sun(d)
        return toEquatorial(s.x, s.y, 0.0, d)
    }

    fun planetPosition(p: Planet, instant: Instant): Equatorial {
        val d = dayNumber(instant)
        val el = elements(p, d)
        val ea = eccentricAnomaly(el.m, el.e)
        val xv = el.a * (cos(ea) - el.e)
        val yv = el.a * sqrt(1 - el.e * el.e) * sin(ea)
        val v = atan2(yv, xv)
        val r = sqrt(xv * xv + yv * yv)
        val n = el.n * RAD
        val i = el.i * RAD
        val vw = v + el.w * RAD
        val xh = r * (cos(n) * cos(vw) - sin(n) * sin(vw) * cos(i))
        val yh = r * (sin(n) * cos(vw) + cos(n) * sin(vw) * cos(i))
        val zh = r * sin(vw) * sin(i)
        val s = sun(d)
        return toEquatorial(xh + s.x, yh + s.y, zh, d)
    }

    /** The Moon, with its largest perturbations (good to about a quarter of a degree). */
    fun moonPosition(instant: Instant): Equatorial {
        val d = dayNumber(instant)
        val n = 125.1228 - 0.0529538083 * d
        val i = 5.1454
        val w = 318.0634 + 0.1643573223 * d
        val e = 0.054900
        val m = 115.3654 + 13.0649929509 * d
        val ea = eccentricAnomaly(m, e)
        val xv = cos(ea) - e
        val yv = sqrt(1 - e * e) * sin(ea)
        val v = atan2(yv, xv)
        val vw = v + w * RAD
        val nr = n * RAD
        val ir = i * RAD
        val xh = cos(nr) * cos(vw) - sin(nr) * sin(vw) * cos(ir)
        val yh = sin(nr) * cos(vw) + cos(nr) * sin(vw) * cos(ir)
        val zh = sin(vw) * sin(ir)
        var lon = atan2(yh, xh) * DEG
        var lat = atan2(zh, sqrt(xh * xh + yh * yh)) * DEG
        val s = sun(d)
        val ms = s.meanAnomaly * RAD
        val mm = rev(m) * RAD
        val lm = rev(n + w + m)
        val dd = rev(lm - s.meanLon) * RAD
        val f = rev(lm - n) * RAD
        lon += -1.274 * sin(mm - 2 * dd) + 0.658 * sin(2 * dd) - 0.186 * sin(ms) - 0.059 * sin(2 * mm - 2 * dd) -
            0.057 * sin(mm - 2 * dd + ms) + 0.053 * sin(mm + 2 * dd) + 0.046 * sin(2 * dd - ms) + 0.041 * sin(mm - ms) -
            0.035 * sin(dd) - 0.031 * sin(mm + ms)
        lat += -0.173 * sin(f - 2 * dd) - 0.055 * sin(mm - f - 2 * dd) - 0.046 * sin(mm + f - 2 * dd) + 0.033 * sin(f + 2 * dd) + 0.017 * sin(2 * mm + f)
        val x = cos(lon * RAD) * cos(lat * RAD)
        val y = sin(lon * RAD) * cos(lat * RAD)
        val z = sin(lat * RAD)
        return toEquatorial(x, y, z, d)
    }

    /** Local sidereal time in degrees. */
    fun siderealDeg(instant: Instant, longitude: Double): Double {
        val d = dayNumber(instant)
        val s = sun(d)
        val ut = (instant.toEpochMilli() % 86_400_000L + 86_400_000L) % 86_400_000L / 3_600_000.0
        return rev(s.meanLon + 180 + ut * 15 + longitude)
    }

    /** Altitude above the horizon and azimuth (from north, through east), in degrees. */
    fun horizontal(eq: Equatorial, siderealDeg: Double, latitude: Double): Horizontal {
        val ha = (siderealDeg - eq.raDeg) * RAD
        val dec = eq.decDeg * RAD
        val lat = latitude * RAD
        val x = cos(ha) * cos(dec)
        val y = sin(ha) * cos(dec)
        val z = sin(dec)
        val xh = x * sin(lat) - z * cos(lat)
        val zh = x * cos(lat) + z * sin(lat)
        return Horizontal(asin(zh.coerceIn(-1.0, 1.0)) * DEG, rev(atan2(y, xh) * DEG + 180))
    }

    // ---------------------------------------------------------------- Bright stars

    /** A star: right ascension in hours, declination in degrees, visual magnitude. */
    class Star(val name: String, val raH: Double, val dec: Double, val mag: Double)

    class Constellation(val name: String, val lines: List<Pair<String, String>>)

    val stars: List<Star> = listOf(
        // Orion
        Star("Betelgeuse", 5.919, 7.41, 0.5), Star("Rigel", 5.242, -8.20, 0.13), Star("Bellatrix", 5.419, 6.35, 1.64),
        Star("Saiph", 5.796, -9.67, 2.06), Star("Alnitak", 5.679, -1.94, 1.77), Star("Alnilam", 5.603, -1.20, 1.69),
        Star("Mintaka", 5.533, -0.30, 2.23), Star("Meissa", 5.585, 9.93, 3.4),
        // Ursa Major
        Star("Dubhe", 11.062, 61.75, 1.8), Star("Merak", 11.031, 56.38, 2.37), Star("Phecda", 11.897, 53.69, 2.44),
        Star("Megrez", 12.257, 57.03, 3.31), Star("Alioth", 12.900, 55.96, 1.77), Star("Mizar", 13.399, 54.93, 2.27),
        Star("Alkaid", 13.792, 49.31, 1.86),
        // Ursa Minor
        Star("Polaris", 2.530, 89.26, 1.98), Star("Kochab", 14.845, 74.16, 2.08), Star("Pherkad", 15.345, 71.83, 3.0),
        Star("Yildun", 17.537, 86.59, 4.35), Star("Eps UMi", 16.766, 82.04, 4.2), Star("Zeta UMi", 15.734, 77.79, 4.3),
        Star("Eta UMi", 16.292, 75.76, 4.95),
        // Cassiopeia
        Star("Caph", 0.153, 59.15, 2.28), Star("Schedar", 0.675, 56.54, 2.24), Star("Gamma Cas", 0.945, 60.72, 2.15),
        Star("Ruchbah", 1.430, 60.24, 2.66), Star("Segin", 1.907, 63.67, 3.35),
        // Cygnus
        Star("Deneb", 20.690, 45.28, 1.25), Star("Sadr", 20.370, 40.26, 2.23), Star("Gienah", 20.770, 33.97, 2.48),
        Star("Delta Cyg", 19.750, 45.13, 2.87), Star("Albireo", 19.512, 27.96, 3.05),
        // Lyra
        Star("Vega", 18.616, 38.78, 0.03), Star("Zeta Lyr", 18.746, 37.60, 4.3), Star("Sheliak", 18.835, 33.36, 3.5),
        Star("Sulafat", 18.982, 32.69, 3.25), Star("Delta Lyr", 18.908, 36.90, 4.3),
        // Aquila
        Star("Altair", 19.846, 8.87, 0.77), Star("Tarazed", 19.771, 10.61, 2.72), Star("Alshain", 19.922, 6.41, 3.71),
        // Scorpius
        Star("Antares", 16.490, -26.43, 1.06), Star("Acrab", 16.091, -19.81, 2.62), Star("Dschubba", 16.006, -22.62, 2.29),
        Star("Pi Sco", 15.981, -26.11, 2.89), Star("Tau Sco", 16.598, -28.22, 2.82), Star("Eps Sco", 16.836, -34.29, 2.29),
        Star("Mu Sco", 16.864, -38.05, 3.0), Star("Zeta Sco", 16.910, -42.36, 3.6), Star("Eta Sco", 17.203, -43.24, 3.3),
        Star("Sargas", 17.622, -43.00, 1.86), Star("Iota Sco", 17.793, -40.13, 3.0), Star("Kappa Sco", 17.708, -39.03, 2.4),
        Star("Shaula", 17.560, -37.10, 1.62), Star("Lesath", 17.513, -37.30, 2.7),
        // Leo
        Star("Regulus", 10.140, 11.97, 1.35), Star("Eta Leo", 10.122, 16.76, 3.5), Star("Algieba", 10.333, 19.84, 2.2),
        Star("Adhafera", 10.278, 23.42, 3.4), Star("Rasalas", 9.879, 26.0, 3.9), Star("Eps Leo", 9.764, 23.77, 2.98),
        Star("Zosma", 11.235, 20.52, 2.56), Star("Denebola", 11.818, 14.57, 2.14), Star("Chertan", 11.237, 15.43, 3.3),
        // Gemini
        Star("Castor", 7.577, 31.89, 1.58), Star("Pollux", 7.755, 28.03, 1.14), Star("Alhena", 6.629, 16.40, 1.93),
        Star("Mebsuta", 6.732, 25.13, 3.0), Star("Tejat", 6.383, 22.51, 2.9), Star("Wasat", 7.335, 21.98, 3.5),
        // Taurus
        Star("Aldebaran", 4.599, 16.51, 0.85), Star("Elnath", 5.438, 28.61, 1.65), Star("Zeta Tau", 5.627, 21.14, 3.0),
        Star("Ain", 4.477, 19.18, 3.5), Star("Gamma Tau", 4.330, 15.63, 3.65), Star("Alcyone", 3.791, 24.11, 2.87),
        // Canis Major / Minor
        Star("Sirius", 6.752, -16.72, -1.46), Star("Mirzam", 6.378, -17.96, 1.98), Star("Wezen", 7.140, -26.39, 1.83),
        Star("Adhara", 6.977, -28.97, 1.5), Star("Aludra", 7.402, -29.30, 2.45),
        Star("Procyon", 7.655, 5.22, 0.34), Star("Gomeisa", 7.453, 8.29, 2.9),
        // Boötes
        Star("Arcturus", 14.261, 19.18, -0.05), Star("Izar", 14.750, 27.07, 2.35), Star("Muphrid", 13.911, 18.40, 2.68),
        Star("Seginus", 14.535, 38.31, 3.0), Star("Nekkar", 15.032, 40.39, 3.5), Star("Delta Boo", 15.258, 33.31, 3.5),
        // Auriga
        Star("Capella", 5.278, 46.00, 0.08), Star("Menkalinan", 5.992, 44.95, 1.9), Star("Mahasim", 5.995, 37.21, 2.6),
        Star("Hassaleh", 4.950, 33.17, 2.7),
        // Pegasus
        Star("Markab", 23.079, 15.21, 2.48), Star("Scheat", 23.063, 28.08, 2.42), Star("Algenib", 0.220, 15.18, 2.83),
        Star("Alpheratz", 0.140, 29.09, 2.06),
        // Sagittarius
        Star("Kaus Australis", 18.403, -34.38, 1.85), Star("Nunki", 18.921, -26.30, 2.05), Star("Ascella", 19.044, -29.88, 2.6),
        Star("Kaus Media", 18.350, -29.83, 2.7), Star("Kaus Borealis", 18.466, -25.42, 2.8), Star("Alnasl", 18.097, -30.42, 2.98),
        Star("Phi Sgr", 18.761, -26.99, 3.2), Star("Tau Sgr", 19.116, -27.67, 3.3),
        // Southern sky
        Star("Acrux", 12.443, -63.10, 0.77), Star("Mimosa", 12.795, -59.69, 1.25), Star("Gacrux", 12.519, -57.11, 1.64),
        Star("Imai", 12.252, -58.75, 2.8), Star("Rigil Kentaurus", 14.660, -60.84, -0.27), Star("Hadar", 14.064, -60.37, 0.61),
        Star("Canopus", 6.399, -52.70, -0.74), Star("Achernar", 1.629, -57.24, 0.46), Star("Fomalhaut", 22.961, -29.62, 1.16),
        Star("Spica", 13.420, -11.16, 0.97),
        // Andromeda and Perseus
        Star("Mirach", 1.162, 35.62, 2.05), Star("Almach", 2.065, 42.33, 2.1),
        Star("Mirfak", 3.405, 49.86, 1.8), Star("Algol", 3.136, 40.96, 2.1), Star("Delta Per", 3.715, 47.79, 3.0),
        Star("Eps Per", 3.964, 40.01, 2.9), Star("Gamma Per", 3.080, 53.51, 2.9),
        // Capricornus
        Star("Algedi", 20.300, -12.51, 3.6), Star("Dabih", 20.350, -14.78, 3.05), Star("Theta Cap", 21.099, -17.23, 4.1),
        Star("Nashira", 21.668, -16.66, 3.7), Star("Deneb Algedi", 21.784, -16.13, 2.85), Star("Zeta Cap", 21.444, -22.41, 3.7),
        Star("Omega Cap", 20.864, -26.92, 4.1), Star("Psi Cap", 20.768, -25.27, 4.1),
        // Hercules keystone
        Star("Zeta Her", 16.688, 31.60, 2.8), Star("Eta Her", 16.715, 38.92, 3.5), Star("Pi Her", 17.251, 36.81, 3.15), Star("Eps Her", 17.005, 30.93, 3.9),
        // Others
        Star("Diphda", 0.727, -17.99, 2.04), Star("Menkar", 3.038, 4.09, 2.5), Star("Alphecca", 15.578, 26.71, 2.2),
        Star("Eltanin", 17.943, 51.49, 2.2), Star("Rasalhague", 17.582, 12.56, 2.08), Star("Alphard", 9.460, -8.66, 2.0),
        Star("Hamal", 2.120, 23.46, 2.0), Star("Sheratan", 1.911, 20.81, 2.65),
    )

    val constellations: List<Constellation> = listOf(
        Constellation("Orion", listOf("Meissa" to "Betelgeuse", "Meissa" to "Bellatrix", "Betelgeuse" to "Alnitak", "Bellatrix" to "Mintaka", "Mintaka" to "Alnilam", "Alnilam" to "Alnitak", "Alnitak" to "Saiph", "Mintaka" to "Rigel")),
        Constellation("Big Dipper", listOf("Dubhe" to "Merak", "Merak" to "Phecda", "Phecda" to "Megrez", "Megrez" to "Dubhe", "Megrez" to "Alioth", "Alioth" to "Mizar", "Mizar" to "Alkaid")),
        Constellation("Little Dipper", listOf("Polaris" to "Yildun", "Yildun" to "Eps UMi", "Eps UMi" to "Zeta UMi", "Zeta UMi" to "Kochab", "Kochab" to "Pherkad", "Pherkad" to "Eta UMi", "Eta UMi" to "Zeta UMi")),
        Constellation("Cassiopeia", listOf("Caph" to "Schedar", "Schedar" to "Gamma Cas", "Gamma Cas" to "Ruchbah", "Ruchbah" to "Segin")),
        Constellation("Cygnus", listOf("Deneb" to "Sadr", "Sadr" to "Albireo", "Gienah" to "Sadr", "Sadr" to "Delta Cyg")),
        Constellation("Lyra", listOf("Vega" to "Zeta Lyr", "Zeta Lyr" to "Sheliak", "Sheliak" to "Sulafat", "Sulafat" to "Delta Lyr", "Delta Lyr" to "Zeta Lyr")),
        Constellation("Aquila", listOf("Tarazed" to "Altair", "Altair" to "Alshain")),
        Constellation("Scorpius", listOf("Acrab" to "Dschubba", "Dschubba" to "Pi Sco", "Dschubba" to "Antares", "Antares" to "Tau Sco", "Tau Sco" to "Eps Sco", "Eps Sco" to "Mu Sco", "Mu Sco" to "Zeta Sco", "Zeta Sco" to "Eta Sco", "Eta Sco" to "Sargas", "Sargas" to "Iota Sco", "Iota Sco" to "Kappa Sco", "Kappa Sco" to "Shaula", "Shaula" to "Lesath")),
        Constellation("Leo", listOf("Regulus" to "Eta Leo", "Eta Leo" to "Algieba", "Algieba" to "Adhafera", "Adhafera" to "Rasalas", "Rasalas" to "Eps Leo", "Algieba" to "Zosma", "Zosma" to "Denebola", "Denebola" to "Chertan", "Chertan" to "Regulus", "Chertan" to "Zosma")),
        Constellation("Gemini", listOf("Castor" to "Pollux", "Castor" to "Mebsuta", "Mebsuta" to "Tejat", "Pollux" to "Wasat", "Wasat" to "Alhena")),
        Constellation("Taurus", listOf("Zeta Tau" to "Aldebaran", "Aldebaran" to "Gamma Tau", "Gamma Tau" to "Ain", "Ain" to "Elnath")),
        Constellation("Canis Major", listOf("Mirzam" to "Sirius", "Sirius" to "Wezen", "Wezen" to "Adhara", "Wezen" to "Aludra")),
        Constellation("Canis Minor", listOf("Procyon" to "Gomeisa")),
        Constellation("Boötes", listOf("Arcturus" to "Izar", "Izar" to "Delta Boo", "Delta Boo" to "Nekkar", "Nekkar" to "Seginus", "Seginus" to "Arcturus", "Arcturus" to "Muphrid")),
        Constellation("Auriga", listOf("Capella" to "Menkalinan", "Menkalinan" to "Mahasim", "Mahasim" to "Elnath", "Elnath" to "Hassaleh", "Hassaleh" to "Capella")),
        Constellation("Pegasus", listOf("Markab" to "Scheat", "Scheat" to "Alpheratz", "Alpheratz" to "Algenib", "Algenib" to "Markab")),
        Constellation("Sagittarius", listOf("Alnasl" to "Kaus Media", "Alnasl" to "Kaus Australis", "Kaus Media" to "Kaus Australis", "Kaus Australis" to "Ascella", "Ascella" to "Phi Sgr", "Phi Sgr" to "Kaus Media", "Kaus Media" to "Kaus Borealis", "Kaus Borealis" to "Phi Sgr", "Phi Sgr" to "Nunki", "Nunki" to "Tau Sgr", "Tau Sgr" to "Ascella")),
        Constellation("Southern Cross", listOf("Acrux" to "Gacrux", "Mimosa" to "Imai")),
        Constellation("Andromeda", listOf("Alpheratz" to "Mirach", "Mirach" to "Almach")),
        Constellation("Perseus", listOf("Gamma Per" to "Mirfak", "Mirfak" to "Delta Per", "Delta Per" to "Eps Per", "Mirfak" to "Algol")),
        Constellation("Capricornus", listOf("Algedi" to "Dabih", "Dabih" to "Theta Cap", "Theta Cap" to "Nashira", "Nashira" to "Deneb Algedi", "Deneb Algedi" to "Zeta Cap", "Zeta Cap" to "Omega Cap", "Omega Cap" to "Psi Cap", "Psi Cap" to "Dabih")),
        Constellation("Hercules", listOf("Zeta Her" to "Eta Her", "Eta Her" to "Pi Her", "Pi Her" to "Eps Her", "Eps Her" to "Zeta Her")),
        Constellation("Aries", listOf("Hamal" to "Sheratan")),
    )

    /** Points along the Milky Way (galactic latitude [b], longitude [l]) in equatorial coordinates. */
    fun galactic(l: Double, b: Double): Equatorial {
        val raP = 192.85948 * RAD
        val decP = 27.12825 * RAD
        val lNcp = 122.93192 * RAD
        val lr = l * RAD
        val br = b * RAD
        val sinDec = sin(decP) * sin(br) + cos(decP) * cos(br) * cos(lNcp - lr)
        val ra = raP + atan2(cos(br) * sin(lNcp - lr), cos(decP) * sin(br) - sin(decP) * cos(br) * cos(lNcp - lr))
        return Equatorial(rev(ra * DEG), asin(sinDec.coerceIn(-1.0, 1.0)) * DEG)
    }

    /** Angular distance between two directions on the sky, in degrees. */
    fun separation(a: Equatorial, b: Equatorial): Double {
        val c = sin(a.decDeg * RAD) * sin(b.decDeg * RAD) + cos(a.decDeg * RAD) * cos(b.decDeg * RAD) * cos((a.raDeg - b.raDeg) * RAD)
        return kotlin.math.acos(c.coerceIn(-1.0, 1.0)) * DEG
    }

    /** Smallest difference between two angles, -180..180. */
    fun angleDiff(a: Double, b: Double): Double {
        var x = (a - b) % 360
        if (x > 180) x -= 360
        if (x < -180) x += 360
        return x
    }
}
