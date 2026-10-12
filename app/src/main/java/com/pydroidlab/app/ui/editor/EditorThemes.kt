package com.pydroidlab.app.ui.editor

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019
import com.pydroidlab.app.ui.settings.EditorColorScheme as SchemeKind

/** Maps the app-level scheme enum to a concrete sora-editor color scheme. */
object EditorThemes {
    fun resolve(kind: SchemeKind, dark: Boolean): EditorColorScheme = when (kind) {
        SchemeKind.DARCULA -> SchemeDarcula()
        SchemeKind.GITHUB -> SchemeGitHub()
        SchemeKind.MONOKAI -> if (dark) SchemeDarcula() else SchemeEclipse()
        SchemeKind.SOLARIZED_DARK -> SchemeVS2019()
        SchemeKind.SOLARIZED_LIGHT -> SchemeNotepadXX()
    }
}
