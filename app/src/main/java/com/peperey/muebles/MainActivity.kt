package com.peperey.muebles

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
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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

data class Pieza(
    val nombre: String,
    val anchoCm: Float,
    val altoCm: Float,
    val grosorCm: Float,
    val cantidad: Int
) {
    val volumenM3: Float
        get() = anchoCm * altoCm * grosorCm / 1_000_000f * cantidad
}

enum class Madera(val etiqueta: String, val color: Color) {
    PINO("Pino", Color(0xFFE3C08D)),
    ROBLE("Roble", Color(0xFFB08850)),
    NOGAL("Nogal", Color(0xFF6B4A2B)),
    HAYA("Haya", Color(0xFFF0D2A8))
}

@Composable
fun DisenoMueblesScreen() {
    val piezas = remember { mutableStateListOf<Pieza>() }
    var madera by remember { mutableStateOf(Madera.PINO) }
    var nombre by remember { mutableStateOf("") }
    var ancho by remember { mutableStateOf("") }
    var alto by remember { mutableStateOf("") }
    var grosor by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("1") }

    fun anadirPieza() {
        val a = ancho.replace(',', '.').toFloatOrNull()
        val al = alto.replace(',', '.').toFloatOrNull()
        val g = grosor.replace(',', '.').toFloatOrNull()
        val c = cantidad.toIntOrNull()
        if (a == null || al == null || g == null || c == null) return
        if (a <= 0f || al <= 0f || g <= 0f || c <= 0) return
        piezas.add(Pieza(nombre.ifBlank { "Pieza ${piezas.size + 1}" }, a, al, g, c))
        nombre = ""
        ancho = ""
        alto = ""
        grosor = ""
        cantidad = "1"
    }

    val volumenTotal = piezas.sumOf { it.volumenM3.toDouble() }

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
            Text("Vista frontal", style = MaterialTheme.typography.titleSmall)
            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                if (piezas.isEmpty()) return@Canvas
                val altoMax = piezas.maxOf { it.altoCm }
                val anchoTotal = piezas.sumOf { it.anchoCm.toDouble() }.toFloat()
                val separacion = 8f
                val escala = minOf(
                    size.height / altoMax,
                    (size.width - separacion * (piezas.size - 1)) / anchoTotal
                )
                var x = 0f
                piezas.forEach { p ->
                    val w = p.anchoCm * escala
                    val h = p.altoCm * escala
                    drawRect(
                        color = madera.color,
                        topLeft = Offset(x, size.height - h),
                        size = Size(w, h)
                    )
                    x += w + separacion
                }
            }
        }

        item {
            Text(
                "Piezas: ${piezas.size}  ·  Volumen: ${"%.4f".format(volumenTotal)} m³",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        itemsIndexed(piezas) { indice, p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.nombre, style = MaterialTheme.typography.titleSmall)
                    Text("${p.anchoCm} × ${p.altoCm} × ${p.grosorCm} cm · ${p.cantidad} ud.")
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
