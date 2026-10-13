package com.peperey.muebles

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                DisenoMueblesScreen()
            }
        }
    }
}

enum class Orientacion(val etiqueta: String) {
    DE_PIE("De pie"),
    DE_LADO("De lado"),
    TUMBADA("Tumbada")
}

data class Pieza(
    val nombre: String,
    val anchoCm: Float,
    val altoCm: Float,
    val grosorCm: Float,
    val cantidad: Int,
    val orientacion: Orientacion = Orientacion.DE_PIE,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    val volumenM3: Float
        get() = anchoCm * altoCm * grosorCm / 1_000_000f * cantidad

    // Tamaño real de la pieza en los ejes X, Y y Z según su orientación
    val dx: Float
        get() = if (orientacion == Orientacion.DE_LADO) grosorCm else anchoCm

    val dy: Float
        get() = if (orientacion == Orientacion.TUMBADA) grosorCm else altoCm

    val dz: Float
        get() = when (orientacion) {
            Orientacion.DE_PIE -> grosorCm
            Orientacion.DE_LADO -> anchoCm
            Orientacion.TUMBADA -> altoCm
        }
}

// precioM3: precio de ejemplo en euros por metro cúbico. Cámbialo por el de tu zona.
enum class Madera(val etiqueta: String, val color: Color, val precioM3: Double) {
    PINO("Pino", Color(0xFFE3C08D), 300.0),
    ROBLE("Roble", Color(0xFFB08850), 900.0),
    NOGAL("Nogal", Color(0xFF6B4A2B), 1100.0),
    HAYA("Haya", Color(0xFFF0D2A8), 500.0)
}

// Dos piezas se solapan si sus cajas en el espacio se cruzan (tocarse no cuenta)
fun seSolapan(a: Pieza, b: Pieza, eps: Float = 0.01f): Boolean =
    a.x < b.x + b.dx - eps && b.x < a.x + a.dx - eps &&
        a.y < b.y + b.dy - eps && b.y < a.y + a.dy - eps &&
        a.z < b.z + b.dz - eps && b.z < a.z + a.dz - eps

// Plantilla: estantería con laterales, base, techo y baldas repartidas a la misma distancia
fun plantillaEstanteria(ancho: Float, alto: Float, fondo: Float, grosor: Float, baldas: Int): List<Pieza> {
    val t = grosor
    val piezas = mutableListOf<Pieza>()
    piezas += Pieza("Lateral izquierdo", fondo, alto, t, 1, Orientacion.DE_LADO, 0f, 0f, 0f)
    piezas += Pieza("Lateral derecho", fondo, alto, t, 1, Orientacion.DE_LADO, ancho - t, 0f, 0f)
    piezas += Pieza("Base", ancho - 2 * t, fondo, t, 1, Orientacion.TUMBADA, t, 0f, 0f)
    piezas += Pieza("Techo", ancho - 2 * t, fondo, t, 1, Orientacion.TUMBADA, t, alto - t, 0f)
    val libre = alto - 2 * t - baldas * t
    val hueco = libre / (baldas + 1)
    for (i in 1..baldas) {
        val y = t + i * hueco + (i - 1) * t
        piezas += Pieza("Balda $i", ancho - 2 * t, fondo, t, 1, Orientacion.TUMBADA, t, y, 0f)
    }
    return piezas
}

// Plantilla: mesa con tablero y cuatro patas cuadradas de 5 cm
fun plantillaMesa(ancho: Float, fondo: Float, alto: Float, grosor: Float): List<Pieza> {
    val pata = 5f
    return listOf(
        Pieza("Tablero", ancho, fondo, grosor, 1, Orientacion.TUMBADA, 0f, alto - grosor, 0f),
        Pieza("Pata 1", pata, alto - grosor, pata, 1, Orientacion.DE_PIE, 0f, 0f, 0f),
        Pieza("Pata 2", pata, alto - grosor, pata, 1, Orientacion.DE_PIE, ancho - pata, 0f, 0f),
        Pieza("Pata 3", pata, alto - grosor, pata, 1, Orientacion.DE_PIE, 0f, 0f, fondo - pata),
        Pieza("Pata 4", pata, alto - grosor, pata, 1, Orientacion.DE_PIE, ancho - pata, 0f, fondo - pata)
    )
}

// Llama a la API de Groq y devuelve el texto de la respuesta. Se ejecuta en segundo plano.
// La IA solo extrae los datos del mueble; la geometría la calcula la app con las plantillas.
fun llamarGroq(clave: String, descripcion: String): String {
    val sistema = """Extraes los datos de un mueble de madera a partir de una descripción. Responde SOLO con un objeto JSON, sin texto ni markdown, con estas claves: "tipo" ("estanteria" o "mesa"), "ancho", "alto", "fondo" y "grosor" (números en cm, positivos), y "baldas" (número entero de baldas; 0 si es una mesa). Si el grosor no se indica, usa 2. Ancho es la medida horizontal de frente, alto la vertical y fondo la profundidad."""

    val cuerpo = JSONObject().apply {
        put("model", "openai/gpt-oss-120b")
        put("temperature", 0.0)
        put(
            "messages",
            JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sistema))
                put(JSONObject().put("role", "user").put("content", descripcion))
            }
        )
    }

    val conexion = URL("https://api.groq.com/openai/v1/chat/completions")
        .openConnection() as HttpURLConnection
    conexion.requestMethod = "POST"
    conexion.setRequestProperty("Content-Type", "application/json")
    conexion.setRequestProperty("Authorization", "Bearer $clave")
    conexion.connectTimeout = 15000
    conexion.readTimeout = 60000
    conexion.doOutput = true
    conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }

    val codigo = conexion.responseCode
    val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
        .bufferedReader()
        .use { it.readText() }
    if (codigo !in 200..299) {
        throw IllegalStateException("Error $codigo: $texto")
    }
    return JSONObject(texto)
        .getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .getString("content")
}

// Lee los datos del mueble, los valida y construye las piezas con las plantillas.
fun interpretarMueble(texto: String): List<Pieza> {
    val inicio = texto.indexOf('{')
    val fin = texto.lastIndexOf('}')
    if (inicio < 0 || fin <= inicio) {
        throw IllegalStateException("La respuesta no contiene los datos del mueble")
    }
    val o = JSONObject(texto.substring(inicio, fin + 1))
    val tipo = o.optString("tipo", "").lowercase()
    val ancho = o.optDouble("ancho", Double.NaN).toFloat()
    val alto = o.optDouble("alto", Double.NaN).toFloat()
    val fondo = o.optDouble("fondo", Double.NaN).toFloat()
    val grosor = o.optDouble("grosor", 2.0).toFloat()
    val baldas = o.optInt("baldas", 0)

    if (ancho.isNaN() || alto.isNaN() || fondo.isNaN()) {
        throw IllegalStateException("Faltan medidas de ancho, alto o fondo")
    }

    return when (tipo) {
        "estanteria" -> {
            if (grosor <= 0f || ancho <= 2 * grosor || alto <= 2 * grosor + baldas * grosor ||
                fondo <= 0f || baldas < 0 || baldas > 10
            ) {
                throw IllegalStateException("Medidas no válidas para una estantería")
            }
            plantillaEstanteria(ancho, alto, fondo, grosor, baldas)
        }
        "mesa" -> {
            if (grosor <= 0f || ancho <= 5f || fondo <= 5f || alto <= grosor) {
                throw IllegalStateException("Medidas no válidas para una mesa")
            }
            plantillaMesa(ancho, fondo, alto, grosor)
        }
        else -> throw IllegalStateException("Tipo de mueble no reconocido: $tipo")
    }
}

data class Proyeccion(
    val h0: Float,
    val h1: Float,
    val v0: Float,
    val v1: Float,
    val conflicto: Boolean,
    val color: Color,
    val texto: String
)

fun DrawScope.dibujarProyeccion(proys: List<Proyeccion>, colorTexto: Int) {
    if (proys.isEmpty()) return
    val margen = 16f
    val minH = proys.minOf { it.h0 }
    val maxH = proys.maxOf { it.h1 }
    val minV = proys.minOf { it.v0 }
    val maxV = proys.maxOf { it.v1 }
    val anchoRef = maxOf(maxH - minH, 1f)
    val altoRef = maxOf(maxV - minV, 1f)
    val escala = minOf(
        (size.width - 2 * margen) / anchoRef,
        (size.height - 2 * margen) / altoRef
    )

    // Línea de suelo
    drawLine(
        color = Color.Gray,
        start = Offset(margen, size.height - margen),
        end = Offset(size.width - margen, size.height - margen),
        strokeWidth = 2f
    )

    val paint = android.graphics.Paint().apply {
        textSize = 26f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        color = colorTexto
    }

    proys.forEach { p ->
        val left = margen + (p.h0 - minH) * escala
        val top = size.height - margen - (p.v1 - minV) * escala
        val w = (p.h1 - p.h0) * escala
        val h = (p.v1 - p.v0) * escala
        drawRect(
            color = if (p.conflicto) Color(0xFFD32F2F) else p.color,
            topLeft = Offset(left, top),
            size = Size(w, h)
        )
        drawRect(
            color = Color.Black,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(2f)
        )
        if (w >= 70f && h >= 40f) {
            drawContext.canvas.nativeCanvas.drawText(
                p.texto,
                left + w / 2f,
                top + h / 2f + paint.textSize / 3f,
                paint
            )
        }
    }
}

// ---------- Vista 3D isométrica ----------

private data class V3(val x: Float, val y: Float, val z: Float)

private class Cara(val indices: IntArray, val normal: V3)

private class Poligono(val profundidad: Float, val puntos: List<Pair<Float, Float>>, val color: Color)

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

        CARAS.forEach { cara ->
            val n = rotarY(cara.normal, cosA, sinA)
            // Solo se dibujan las caras que miran al observador
            if (producto(n, vista) <= 0f) return@forEach
            val brillo = 0.55f + 0.45f * max(0f, producto(n, LUZ))
            val color = lerp(Color.Black, base, brillo)
            val puntos = cara.indices.map { idx ->
                val e = esquinas[idx]
                Pair(e.x, -e.y * cosE + e.z * sinE)
            }
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
}

private const val PREFS = "muebles_prefs"

fun guardarDiseno(context: Context, piezas: List<Pieza>, madera: Madera) {
    val array = JSONArray()
    piezas.forEach { p ->
        array.put(
            JSONObject().apply {
                put("nombre", p.nombre)
                put("ancho", p.anchoCm.toDouble())
                put("alto", p.altoCm.toDouble())
                put("grosor", p.grosorCm.toDouble())
                put("cantidad", p.cantidad)
                put("orientacion", p.orientacion.name)
                put("x", p.x.toDouble())
                put("y", p.y.toDouble())
                put("z", p.z.toDouble())
            }
        )
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("piezas", array.toString())
        .putString("madera", madera.name)
        .apply()
}

fun cargarDiseno(context: Context): Pair<List<Pieza>, Madera> {
    return runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lista = mutableListOf<Pieza>()
        val texto = prefs.getString("piezas", null)
        if (texto != null) {
            val array = JSONArray(texto)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                lista.add(
                    Pieza(
                        nombre = o.getString("nombre"),
                        anchoCm = o.getDouble("ancho").toFloat(),
                        altoCm = o.getDouble("alto").toFloat(),
                        grosorCm = o.getDouble("grosor").toFloat(),
                        cantidad = o.getInt("cantidad"),
                        orientacion = runCatching {
                            Orientacion.valueOf(o.optString("orientacion", "DE_PIE"))
                        }.getOrDefault(Orientacion.DE_PIE),
                        x = o.optDouble("x", 0.0).toFloat(),
                        y = o.optDouble("y", 0.0).toFloat(),
                        z = o.optDouble("z", 0.0).toFloat()
                    )
                )
            }
        }
        val madera = prefs.getString("madera", null)
            ?.let { runCatching { Madera.valueOf(it) }.getOrNull() }
            ?: Madera.PINO
        lista to madera
    }.getOrElse { emptyList<Pieza>() to Madera.PINO }
}

// La clave se guarda solo en el teléfono, en las preferencias de la app
fun guardarClave(context: Context, clave: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("groq_key", clave)
        .apply()
}

fun cargarClave(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("groq_key", "") ?: ""

@Composable
fun DisenoMueblesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inicial = remember { cargarDiseno(context) }
    val piezas = remember { mutableStateListOf<Pieza>().apply { addAll(inicial.first) } }
    var madera by remember { mutableStateOf(inicial.second) }
    var nombre by remember { mutableStateOf("") }
    var orientacion by remember { mutableStateOf(Orientacion.DE_PIE) }
    var ancho by remember { mutableStateOf("") }
    var alto by remember { mutableStateOf("") }
    var grosor by remember { mutableStateOf("") }
    var px by remember { mutableStateOf("0") }
    var py by remember { mutableStateOf("0") }
    var pz by remember { mutableStateOf("0") }
    var cantidad by remember { mutableStateOf("1") }
    var tAncho by remember { mutableStateOf("") }
    var tAlto by remember { mutableStateOf("") }
    var tFondo by remember { mutableStateOf("") }
    var tGrosor by remember { mutableStateOf("") }
    var tBaldas by remember { mutableStateOf("3") }
    var angulo by remember { mutableFloatStateOf(30f) }

    // Estado de la IA
    var clave by remember { mutableStateOf(cargarClave(context)) }
    var descripcion by remember { mutableStateOf("") }
    var propuesta by remember { mutableStateOf<List<Pieza>>(emptyList()) }
    var estadoIA by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }

    // Guarda el diseño cada vez que cambian las piezas o la madera
    LaunchedEffect(piezas.toList(), madera) {
        guardarDiseno(context, piezas.toList(), madera)
    }

    fun anadirPieza() {
        val a = ancho.replace(',', '.').toFloatOrNull()
        val al = alto.replace(',', '.').toFloatOrNull()
        val g = grosor.replace(',', '.').toFloatOrNull()
        val c = cantidad.toIntOrNull()
        if (a == null || al == null || g == null || c == null) return
        if (a <= 0f || al <= 0f || g <= 0f || c <= 0) return
        piezas.add(
            Pieza(
                nombre = nombre.ifBlank { "Pieza ${piezas.size + 1}" },
                anchoCm = a,
                altoCm = al,
                grosorCm = g,
                cantidad = c,
                orientacion = orientacion,
                x = px.replace(',', '.').toFloatOrNull() ?: 0f,
                y = py.replace(',', '.').toFloatOrNull() ?: 0f,
                z = pz.replace(',', '.').toFloatOrNull() ?: 0f
            )
        )
        nombre = ""
        ancho = ""
        alto = ""
        grosor = ""
        px = "0"
        py = "0"
        pz = "0"
        cantidad = "1"
    }

    fun aplicarPlantilla(lista: List<Pieza>) {
        piezas.clear()
        piezas.addAll(lista)
    }

    val volumenTotal = piezas.sumOf { it.volumenM3.toDouble() }
    val costeTotal = volumenTotal * madera.precioM3

    // Marca las piezas que se solapan con otra
    val enConflicto = BooleanArray(piezas.size)
    for (i in piezas.indices) {
        for (j in i + 1 until piezas.size) {
            if (seSolapan(piezas[i], piezas[j])) {
                enConflicto[i] = true
                enConflicto[j] = true
            }
        }
    }
    val numConflictos = enConflicto.count { it }

    // Piezas propuestas por la IA que chocan con el diseño actual o con otras propuestas
    val todas = piezas + propuesta
    val solapesPropuesta = propuesta.count { p ->
        todas.any { q -> q !== p && seSolapan(p, q) }
    }

    // Valores de las plantillas
    val tA = tAncho.replace(',', '.').toFloatOrNull()
    val tAl = tAlto.replace(',', '.').toFloatOrNull()
    val tF = tFondo.replace(',', '.').toFloatOrNull()
    val tG = tGrosor.replace(',', '.').toFloatOrNull()
    val tB = tBaldas.toIntOrNull() ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Diseño de muebles de madera", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Text("Madera", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Madera.entries.forEach { m ->
                    FilterChip(
                        selected = m == madera,
                        onClick = { madera = m },
                        label = { Text(m.etiqueta) }
                    )
                }
            }
            Text(
                "${madera.etiqueta}: ${madera.precioM3.toInt()} € por m³ (precio de ejemplo)",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Text("Diseño con IA", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("Clave de Groq") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    guardarClave(context, clave.trim())
                    estadoIA = "Clave guardada en el teléfono"
                },
                enabled = clave.isNotBlank()
            ) {
                Text("Guardar clave")
            }
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Describe el mueble") },
                placeholder = { Text("Ej.: estantería de 90 cm de ancho, 180 de alto, 30 de fondo, con 4 baldas") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    cargando = true
                    estadoIA = "Pensando..."
                    propuesta = emptyList()
                    scope.launch {
                        try {
                            val texto = withContext(Dispatchers.IO) {
                                llamarGroq(clave.trim(), descripcion.trim())
                            }
                            val lista = withContext(Dispatchers.Default) {
                                interpretarMueble(texto)
                            }
                            propuesta = lista
                            estadoIA = "Propuesta: ${lista.size} piezas. Revísala antes de añadirla."
                        } catch (e: Exception) {
                            estadoIA = "Error: ${e.message ?: "desconocido"}"
                        } finally {
                            cargando = false
                        }
                    }
                },
                enabled = !cargando && clave.isNotBlank() && descripcion.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generar propuesta")
            }
            if (estadoIA.isNotBlank()) {
                Text(estadoIA, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (propuesta.isNotEmpty()) {
            item {
                Text("Propuesta de la IA", style = MaterialTheme.typography.titleSmall)
                propuesta.forEach { p ->
                    Text(
                        "${p.nombre}: ${p.anchoCm} × ${p.altoCm} × ${p.grosorCm} cm · " +
                            "${p.orientacion.etiqueta} · X ${p.x}  Y ${p.y}  Z ${p.z}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (solapesPropuesta > 0) {
                    Text(
                        "⚠ $solapesPropuesta piezas propuestas se solapan con el diseño",
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            piezas.addAll(propuesta)
                            propuesta = emptyList()
                            estadoIA = "Piezas añadidas al diseño"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Añadir al diseño")
                    }
                    OutlinedButton(
                        onClick = {
                            propuesta = emptyList()
                            estadoIA = "Propuesta descartada"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Descartar")
                    }
                }
            }
        }

        item {
            Text("Plantillas (sustituyen el diseño actual)", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = tAncho,
                    onValueChange = { tAncho = it },
                    label = { Text("Ancho total") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tAlto,
                    onValueChange = { tAlto = it },
                    label = { Text("Alto total") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tFondo,
                    onValueChange = { tFondo = it },
                    label = { Text("Fondo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = tGrosor,
                    onValueChange = { tGrosor = it },
                    label = { Text("Grosor") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tBaldas,
                    onValueChange = { tBaldas = it },
                    label = { Text("Baldas") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (tA != null && tAl != null && tF != null && tG != null &&
                            tG > 0f && tA > 2 * tG && tF > 0f && tB >= 0 &&
                            tAl > 2 * tG + tB * tG
                        ) {
                            aplicarPlantilla(plantillaEstanteria(tA, tAl, tF, tG, tB))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Estantería")
                }
                Button(
                    onClick = {
                        if (tA != null && tAl != null && tF != null && tG != null &&
                            tG > 0f && tA > 5f && tF > 5f && tAl > tG
                        ) {
                            aplicarPlantilla(plantillaMesa(tA, tF, tAl, tG))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mesa")
                }
            }
        }

        item {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre de la pieza") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("Orientación", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Orientacion.entries.forEach { o ->
                    FilterChip(
                        selected = o == orientacion,
                        onClick = { orientacion = o },
                        label = { Text(o.etiqueta) }
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = ancho,
                    onValueChange = { ancho = it },
                    label = { Text("Ancho cm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = alto,
                    onValueChange = { alto = it },
                    label = { Text("Alto cm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = grosor,
                    onValueChange = { grosor = it },
                    label = { Text("Grosor cm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                "Posición de la esquina inferior (cm)",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = px,
                    onValueChange = { px = it },
                    label = { Text("X") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = py,
                    onValueChange = { py = it },
                    label = { Text("Y") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = pz,
                    onValueChange = { pz = it },
                    label = { Text("Z") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { anadirPieza() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Añadir pieza")
                }
            }
        }

        item {
            Text("Vista frontal (X · Y)", style = MaterialTheme.typography.titleSmall)
            Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                dibujarProyeccion(
                    proys = piezas.mapIndexed { i, p ->
                        Proyeccion(
                            h0 = p.x,
                            h1 = p.x + p.dx,
                            v0 = p.y,
                            v1 = p.y + p.dy,
                            conflicto = enConflicto[i],
                            color = madera.color,
                            texto = "${"%.0f".format(p.dx)} × ${"%.0f".format(p.dy)}"
                        )
                    },
                    colorTexto = if (madera == Madera.NOGAL) {
                        android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.BLACK
                    }
                )
            }
        }

        item {
            Text("Vista lateral (Z · Y)", style = MaterialTheme.typography.titleSmall)
            Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                dibujarProyeccion(
                    proys = piezas.mapIndexed { i, p ->
                        Proyeccion(
                            h0 = p.z,
                            h1 = p.z + p.dz,
                            v0 = p.y,
                            v1 = p.y + p.dy,
                            conflicto = enConflicto[i],
                            color = madera.color,
                            texto = "${"%.0f".format(p.dz)} × ${"%.0f".format(p.dy)}"
                        )
                    },
                    colorTexto = if (madera == Madera.NOGAL) {
                        android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.BLACK
                    }
                )
            }
        }

        item {
            Text("Vista 3D (arrastra el control para girar)", style = MaterialTheme.typography.titleSmall)
            Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                dibujarVista3D(piezas, enConflicto, madera.color, angulo)
            }
            Slider(
                value = angulo,
                onValueChange = { angulo = it },
                valueRange = 0f..360f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text(
                "Piezas: ${piezas.size}  ·  Volumen: ${"%.4f".format(volumenTotal)} m³  ·  " +
                    "Coste: ${"%.2f".format(costeTotal)} €",
                style = MaterialTheme.typography.bodyLarge
            )
            if (numConflictos > 0) {
                Text(
                    "⚠ $numConflictos piezas se solapan (en rojo)",
                    color = Color(0xFFD32F2F),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        itemsIndexed(piezas) { indice, p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.nombre, style = MaterialTheme.typography.titleSmall)
                    Text("${p.anchoCm} × ${p.altoCm} × ${p.grosorCm} cm · ${p.cantidad} ud.")
                    Text(
                        "${p.orientacion.etiqueta} · X ${p.x}  Y ${p.y}  Z ${p.z} cm",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Coste: ${"%.2f".format(p.volumenM3 * madera.precioM3)} €",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (enConflicto[indice]) {
                        Text(
                            "Se solapa con otra pieza",
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                OutlinedButton(onClick = { piezas.removeAt(indice) }) {
                    Text("Quitar")
                }
            }
        }

        item {
            if (piezas.isNotEmpty()) {
                OutlinedButton(onClick = { piezas.clear() }) {
                    Text("Borrar todo")
                }
            }
        }
    }
}
