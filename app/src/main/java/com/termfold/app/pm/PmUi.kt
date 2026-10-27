package com.termfold.app.pm

/** Escape dynamic text passed as [androidx.compose.ui.res.stringResource] format args. */
internal fun String.forStringResourceArg(): String = replace("%", "%%")
