package local.antenna.calculator.ui

import androidx.compose.foundation.text.input.InputTransformation
import local.antenna.calculator.core.input.PositiveDecimalParser

private val unsignedDecimalEdit = Regex("[0-9]*(?:[.,][0-9]*)?")
private val signedDecimalEdit = Regex("[+-]?[0-9]*(?:[.,][0-9]*)?")

/** Accepts intermediate edits while each tool validates its range and precision. */
internal fun CharSequence.acceptsDecimalEdit(
    signed: Boolean = false,
    limit: Int = PositiveDecimalParser.MAX_CHARACTERS,
): Boolean = length <= limit && (if (signed) signedDecimalEdit else unsignedDecimalEdit).matches(this)

internal fun decimalInputTransformation(signed: Boolean = false, limit: Int = PositiveDecimalParser.MAX_CHARACTERS) =
    InputTransformation { if (!asCharSequence().acceptsDecimalEdit(signed, limit)) revertAllChanges() }
