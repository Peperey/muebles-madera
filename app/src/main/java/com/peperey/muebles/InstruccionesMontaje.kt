package com.peperey.muebles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Dentro de la misma altura: primero laterales y patas, luego bases y tableros, después el resto
private fun prioridad(p: Pieza): Int {
    val n = p.nombre.lowercase()
    return when {
        n.contains("lateral") || n.contains("pata") -> 0
        n.contains("base") || n.contains("tablero") -> 1
        else -> 2
    }
}

// Ordena las piezas de abajo arriba y genera un texto por paso, con el uso de tornillos
fun generarPasos(piezas: List<Pieza>): List<String> {
    val orden = piezas.sortedWith(compareBy<Pieza>({ it.y }, { prioridad(it) }))
    return orden.mapIndexed { i, p ->
        val cantidadTexto = if (p.cantidad > 1) ", x${p.cantidad}" else ""
        "Paso ${i + 1}: coloca ${p.nombre.lowercase()} " +
            "(${p.anchoCm} × ${p.altoCm} × ${p.grosorCm} cm$cantidadTexto) " +
            "en X ${p.x} cm, Y ${p.y} cm, Z ${p.z} cm. " +
            "Fíjala con tornillos a las piezas que la tocan."
    }
}

@Composable
fun InstruccionesMontaje(piezas: List<Pieza>) {
    val pasos = remember(piezas) { generarPasos(piezas) }
    val marcados = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Instrucciones de montaje (tornillos)", style = MaterialTheme.typography.titleSmall)
        if (pasos.isEmpty()) {
            Text(
                "Añade piezas o genera un mueble para ver los pasos.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            val hechos = pasos.indices.count { marcados[it] == true }
            Text(
                "Completados: $hechos de ${pasos.size}",
                style = MaterialTheme.typography.bodySmall
            )
            pasos.forEachIndexed { i, paso ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = marcados[i] == true,
                        onCheckedChange = { marcado -> marcados[i] = marcado }
                    )
                    Text(
                        paso,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(top = 12.dp)
                    )
                }
            }
            OutlinedButton(onClick = { marcados.clear() }) {
                Text("Reiniciar")
            }
        }
    }
}
