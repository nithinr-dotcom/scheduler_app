package com.wac.worktrack.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "Deleted · Undo" snackbar shown by the main scaffold. It runs in the scaffold's scope, so
 * leaving the screen that deleted something doesn't cancel the chance to undo.
 */
class UndoHost(val snackbar: SnackbarHostState, private val scope: CoroutineScope) {
    /** Call after the delete is done; [undo] puts it back if the user taps Undo. */
    fun show(message: String, undo: suspend () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
}

val LocalUndo = staticCompositionLocalOf<UndoHost> { error("UndoHost not provided") }

@Composable
fun rememberUndo(): UndoHost = LocalUndo.current
