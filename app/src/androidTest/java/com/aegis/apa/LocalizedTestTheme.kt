package com.aegis.apa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.aegis.apa.localization.LocalAppLanguage
import com.aegis.apa.localization.withAppLanguage
import com.aegis.apa.ui.theme.AndroidPersonalAgentTheme

/** Component fixtures must agree on the language of resource and Kotlin UI copy. */
@Composable
internal fun LocalizedTestTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val localizedContext = remember(context, language) { context.withAppLanguage(language) }
    CompositionLocalProvider(LocalContext provides localizedContext) {
        AndroidPersonalAgentTheme(content = content)
    }
}
