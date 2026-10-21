package com.termfold.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.ui.theme.Palette
import org.json.JSONObject

/**
 * Modifier and navigation keys for the in-app code editor (soft keyboard lacks Ctrl, Tab, etc.).
 */
@Composable
internal fun EditorAccessoryKeys(
    modifierState: EditorModifierState,
    onJs: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var controlDown by remember { mutableStateOf(modifierState.controlDown) }
    var altDown by remember { mutableStateOf(modifierState.altDown) }
    var shiftDown by remember { mutableStateOf(modifierState.shiftDown) }

    fun syncModifiers() {
        modifierState.controlDown = controlDown
        modifierState.shiftDown = shiftDown
        modifierState.altDown = altDown
    }

    fun modsJson(): String = JSONObject()
        .put("ctrl", controlDown)
        .put("shift", shiftDown)
        .put("alt", altDown)
        .toString()

    fun sendKey(key: String) {
        onJs("tf.sendKey(${JSONObject.quote(key)}, ${modsJson()})")
    }

    fun sendChord(key: String) {
        when (key) {
            "s" -> onJs("tf.requestSave()")
            "z" -> onJs("tf.undo()")
            "y" -> onJs("tf.redo()")
            "f" -> onJs("tf.search()")
            "a" -> onJs("tf.sendKey('a',{\"ctrl\":true,\"shift\":false,\"alt\":false})")
            "c" -> onJs("tf.sendKey('c',{\"ctrl\":true,\"shift\":false,\"alt\":false})")
            "x" -> onJs("tf.sendKey('x',{\"ctrl\":true,\"shift\":false,\"alt\":false})")
            "v" -> onJs("tf.sendKey('v',{\"ctrl\":true,\"shift\":false,\"alt\":false})")
            else -> {
                val mods = JSONObject().put("ctrl", true).put("shift", false).put("alt", false).toString()
                onJs("tf.sendKey(${JSONObject.quote(key)}, $mods)")
            }
        }
    }

    fun paste() {
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clip?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        onJs("tf.insertText(${JSONObject.quote(text)})")
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val wide = maxWidth >= 600.dp
        val keyHeight = if (wide) 44.dp else 38.dp
        val gap = if (wide) 8.dp else 5.dp

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            KeyLine(wide = wide, gap = gap) { stretch ->
                ModifierKey(
                    label = stringResource(R.string.key_ctrl),
                    active = controlDown,
                    height = keyHeight,
                    wide = wide,
                    onClick = { controlDown = !controlDown; syncModifiers() },
                    modifier = stretch(1.2f),
                )
                ModifierKey(
                    label = stringResource(R.string.key_shift),
                    active = shiftDown,
                    height = keyHeight,
                    wide = wide,
                    onClick = { shiftDown = !shiftDown; syncModifiers() },
                    modifier = stretch(1.2f),
                )
                ModifierKey(
                    label = stringResource(R.string.key_alt),
                    active = altDown,
                    height = keyHeight,
                    wide = wide,
                    onClick = { altDown = !altDown; syncModifiers() },
                    modifier = stretch(1.2f),
                )
                KeyButton(stringResource(R.string.key_esc), { sendKey("Escape") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_tab), { sendKey("Tab") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_ctrl_s), { sendChord("s") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_ctrl_z), { sendChord("z") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_ctrl_y), { sendChord("y") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_ctrl_f), { sendChord("f") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_ctrl_c), { sendChord("c") }, keyHeight, wide, stretch(0.9f))
                KeyButton(stringResource(R.string.key_ctrl_v), { sendChord("v") }, keyHeight, wide, stretch(0.9f))
                KeyButton(stringResource(R.string.key_paste), { paste() }, keyHeight, wide, stretch(1.1f))
                KeyButton(
                    stringResource(R.string.key_enter),
                    { sendKey("Enter") },
                    keyHeight,
                    wide,
                    stretch(1.4f),
                    primary = true,
                )
            }
            KeyLine(wide = wide, gap = gap) { stretch ->
                KeyButton(stringResource(R.string.key_ctrl_a), { sendChord("a") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_home), { sendKey("Home") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_end), { sendKey("End") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_pgup), { sendKey("PageUp") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_pgdn), { sendKey("PageDown") }, keyHeight, wide, stretch(1f))
                KeyButton(stringResource(R.string.key_pipe), { sendKey("|") }, keyHeight, wide, stretch(0.8f))
                KeyButton(stringResource(R.string.key_slash), { sendKey("/") }, keyHeight, wide, stretch(0.8f))
                KeyButton(stringResource(R.string.key_dash), { sendKey("-") }, keyHeight, wide, stretch(0.8f))
                KeyButton("←", { sendKey("ArrowLeft") }, keyHeight, wide, stretch(0.9f), repeats = true)
                KeyButton("↓", { sendKey("ArrowDown") }, keyHeight, wide, stretch(0.9f), repeats = true)
                KeyButton("↑", { sendKey("ArrowUp") }, keyHeight, wide, stretch(0.9f), repeats = true)
                KeyButton("→", { sendKey("ArrowRight") }, keyHeight, wide, stretch(0.9f), repeats = true)
            }
        }
    }
}
