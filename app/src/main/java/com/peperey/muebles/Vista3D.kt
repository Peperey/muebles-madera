package com.peperey.muebles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class V3(val x: Float, val y: Float, val z: Float)

private class Cara(val indices: IntArray, val normal: V3)

private class Poligono(val profundidad: Float, val puntos: List<Pair<Float, Float>>, val color: Color)

// Etiqueta con el centro proyectado de la pieza y su tamaño en el modelo (antes de escalar)
private class Etiqueta(
    val ecX: Float,
    val ecY: Float,
    val anchoModelo: Float,
    val altoModelo: Float,
    val texto: String
)

// Índice de esquina: bit 0 = X, bit 1 = Y, bit 2 = Z (0 = mínimo, 1 = máximo)
private val CARAS = listOf(
    Cara(intArrayOf(4, 5, 7, 6), V3(0f, 0f, 1f)),
    Cara(intArrayOf(0, 1, 3, 2), V3(0f, 0f, -1f)),
    Cara(intArrayOf(2, 3, 7, 6), V3(0f, 1f, 0f)),
    Cara(intArrayOf(0, 1, 5, 4), V3(0f, -1f, 0f)),
    Cara(intArrayOf(1, 3, 7, 5), V3(1f, 0f, 0f)),
    Cara(intArrayOf(0, 2, 6, 4), V3(-1f, 0f, 0f))
)

// Dirección de la luz, aproximadamente unitaria
private val LUZ = V3(-0.39f, 0.78f, 0.49f)

private fun rotarY(p: V3, cosA: Float, sinA: Float) =
    V3(p.x * cosA + p.z * sinA, p.y, -p.x * sinA + p.z * cosA)

private fun producto(a: V3, b: V3): Float = a.x * b.x + a.y * b.y + a.z * b.z

// Muestra 30 en lugar de 30.0 y 1.8 en lugar de 1.80
private fun formatoCm(v: Float): String =
    if (v % 1f == 0f) "%.0f".format(v) else "%.1f".format(v)

fun DrawScope.dibujarVista3D(
    piezas: List<Pieza>,
    conflicto: BooleanArray,
    colorBase: Color,
    anguloGrados: Float
) {
    if (piezas.isEmpty()) return
    val rad = Math.toRadians(anguloGrados.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    val elev = Math.toRadians(28.0)
    val sinE = sin(elev).toFloat()
    val cosE = cos(elev).toFloat()
    // Dirección desde el mueble hacia el observador: desde arriba y desde delante
    val vista = V3(0f, sinE, cosE)

    val minX = piezas.minOf { it.x }
    val maxX = piezas.maxOf { it.x + it.dx }
    val minY = piezas.minOf { it.y }
    val maxY = piezas.maxOf { it.y + it.dy }
    val minZ = piezas.minOf { it.z }
    val maxZ = piezas.maxOf { it.z + it.dz }
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    val cz = (minZ + maxZ) / 2f

    val poligonos = mutableListOf<Poligono>()
    val etiquetas = mutableListOf<Etiqueta>()

    piezas.forEachIndexed { i, p ->
        val esquinas = Array(8) { idx ->
            val ox = (idx and 1).toFloat()
            val oy = ((idx shr 1) and 1).toFloat()
            val oz = ((idx shr 2) and 1).toFloat()
            rotarY(
                V3(p.x + ox * p.dx - cx, p.y + oy * p.dy - cy, p.z + oz * p.dz - cz),
                cosA,
                sinA
            )
        }
        val centro = rotarY(
            V3(p.x + p.dx / 2f - cx, p.y + p.dy / 2f - cy, p.z + p.dz / 2f - cz),
            cosA,
            sinA
        )
        val profundidad = producto(centro, vista)
        val base = if (conflicto[i]) Color(0xFFD32F2F) else colorBase

        // Proyección de las 8 esquinas, para situar la etiqueta
        val proyectados = esquinas.map { e -> Pair(e.x, -e.y * cosE + e.z * sinE) }
        val pxs = proyectados.map { it.first }
        val pys = proyectados.map { it.second }
        etiquetas += Etiqueta(
            ecX = pxs.average().toFloat(),
            ecY = pys.average().toFloat(),
            anchoModelo = pxs.max() - pxs.min(),
            altoModelo = pys.max() - pys.min(),
            texto = "${formatoCm(p.dx)} × ${formatoCm(p.dy)} × ${formatoCm(p.dz)}"
        )

        CARAS.forEach { cara ->
            val n = rotarY(cara.normal, cosA, sinA)
            // Solo se dibujan las caras que miran al observador
            if (producto(n, vista) <= 0f) return@forEach
            val brillo = 0.55f + 0.45f * max(0f, producto(n, LUZ))
            val color = lerp(Color.Black, base, brillo)
            val puntos = cara.indices.map { idx -> proyectados[idx] }
            poligonos += Poligono(profundidad, puntos, color)
        }
    }

    // Dibuja primero lo más lejano para que lo cercano tape lo que queda detrás
    poligonos.sortBy { it.profundidad }

    val todos = poligonos.flatMap { it.puntos }
    val minSx = todos.minOf { it.first }
    val maxSx = todos.maxOf { it.first }
    val minSy = todos.minOf { it.second }
    val maxSy = todos.maxOf { it.second }
    val margen = 16f
    val escala = min(
        (size.width - 2 * margen) / max(maxSx - minSx, 1f),
        (size.height - 2 * margen) / max(maxSy - minSy, 1f)
    )

    poligonos.forEach { poli ->
        val path = Path()
        poli.puntos.forEachIndexed { k, punto ->
            val px = margen + (punto.first - minSx) * escala
            val py = margen + (punto.second - minSy) * escala
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, poli.color)
        drawPath(path, Color.Black, style = Stroke(1.5f))
    }

    // Medidas sobre cada pieza. En maderas oscuras el texto es blanco para que se lea.
    val paint = android.graphics.Paint().apply {
        textSize = 22f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        color = if ((0.2126f * colorBase.red + 0.7152f * colorBase.green + 0.0722f * colorBase.blue) < 0.45f) {
            android.graphics.Color.WHITE
        } else {
            android.graphics.Color.BLACK
        }
    }
    etiquetas.forEach { et ->
        val anchoPx = et.anchoModelo * escala
        val altoPx = et.altoModelo * escala
        // Si la pieza es demasiado pequeña en pantalla, no se escribe la medida
        if (anchoPx < 90f || altoPx < 26f) return@forEach
        val px = margen + (et.ecX - minSx) * escala
        val py = margen + (et.ecY - minSy) * escala
        drawContext.canvas.nativeCanvas.drawText(et.texto, px, py + paint.textSize / 3f, paint)
    }
}

