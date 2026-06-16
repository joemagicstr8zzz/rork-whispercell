package coil.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    coil3.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
    )
}
