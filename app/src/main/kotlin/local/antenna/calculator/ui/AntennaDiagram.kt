package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

@Composable
fun AntennaDiagram(dipole: Boolean, modifier: Modifier = Modifier) {
    val conductor = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(if (dipole) R.string.diagram_dipole else R.string.diagram_monopole)
    Canvas(modifier.fillMaxWidth().height(if (dipole) 80.dp else 108.dp).semantics { contentDescription = description }) {
        val stroke = 4.dp.toPx()
        val x = size.width / 2
        val y = size.height / 2
        if (dipole) {
            drawLine(conductor, Offset(size.width * .08f, y), Offset(x - 8.dp.toPx(), y), stroke, StrokeCap.Round)
            drawLine(conductor, Offset(x + 8.dp.toPx(), y), Offset(size.width * .92f, y), stroke, StrokeCap.Round)
            drawLine(guide, Offset(x, y + 12.dp.toPx()), Offset(x, size.height), 2.dp.toPx())
            drawCircle(conductor, 3.dp.toPx(), Offset(x - 8.dp.toPx(), y))
            drawCircle(conductor, 3.dp.toPx(), Offset(x + 8.dp.toPx(), y))
        } else {
            val base = size.height * .72f
            drawLine(conductor, Offset(x, 8.dp.toPx()), Offset(x, base), stroke, StrokeCap.Round)
            drawCircle(conductor, 5.dp.toPx(), Offset(x, base))
            drawLine(guide, Offset(size.width * .15f, base), Offset(size.width * .85f, base), 2.dp.toPx())
            drawLine(conductor, Offset(x - 8.dp.toPx(), base + 6.dp.toPx()), Offset(size.width * .27f, size.height - 4.dp.toPx()), 2.dp.toPx())
            drawLine(conductor, Offset(x + 8.dp.toPx(), base + 6.dp.toPx()), Offset(size.width * .73f, size.height - 4.dp.toPx()), 2.dp.toPx())
        }
    }
}
