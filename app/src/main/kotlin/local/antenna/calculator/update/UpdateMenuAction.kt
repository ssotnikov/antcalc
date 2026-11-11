package local.antenna.calculator.update

internal data class UpdateMenuAction(
    val tag: String,
    val title: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)
