package com.furybook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.furybook.shared.generated.resources.Res
import com.furybook.shared.generated.resources.onest_bold
import com.furybook.shared.generated.resources.onest_medium
import com.furybook.shared.generated.resources.onest_regular
import com.furybook.shared.generated.resources.onest_semibold
import com.furybook.shared.generated.resources.unbounded_black
import com.furybook.shared.generated.resources.unbounded_bold
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment

/** Unbounded: headings, numbers and short uppercase labels. */
@Composable
fun furyDisplayFamily(): FontFamily = FontFamily(
    Font(Res.font.unbounded_bold, FontWeight.Bold),
    Font(Res.font.unbounded_black, FontWeight.Black),
)

/** Onest: everything that is read rather than glanced at. */
@Composable
fun furyBodyFamily(): FontFamily = FontFamily(
    Font(Res.font.onest_regular, FontWeight.Normal),
    Font(Res.font.onest_medium, FontWeight.Medium),
    Font(Res.font.onest_semibold, FontWeight.SemiBold),
    Font(Res.font.onest_bold, FontWeight.Bold),
)

@Composable
fun furyTypography(): Typography {
    val display = furyDisplayFamily()
    val body = furyBodyFamily()
    return remember(display, body) {
        fun display(weight: FontWeight, size: Int, line: Int) =
            TextStyle(fontFamily = display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
        fun body(weight: FontWeight, size: Int, line: Int) =
            TextStyle(fontFamily = body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
        Typography(
            displayLarge = display(FontWeight.Black, 48, 48),
            displayMedium = display(FontWeight.Black, 36, 40),
            displaySmall = display(FontWeight.Black, 32, 36),
            headlineLarge = display(FontWeight.Black, 32, 36),
            headlineMedium = display(FontWeight.Black, 28, 32),
            headlineSmall = display(FontWeight.Bold, 22, 26),
            titleLarge = display(FontWeight.Bold, 18, 24),
            titleMedium = body(FontWeight.Bold, 16, 22),
            titleSmall = body(FontWeight.Bold, 14, 20),
            bodyLarge = body(FontWeight.Normal, 16, 24),
            bodyMedium = body(FontWeight.Normal, 14, 20),
            bodySmall = body(FontWeight.Normal, 12, 16),
            labelLarge = body(FontWeight.SemiBold, 14, 18),
            labelMedium = body(FontWeight.SemiBold, 12, 16),
            labelSmall = display(FontWeight.Bold, 10, 14).copy(letterSpacing = 0.06.em),
        )
    }
}

/**
 * False until every bundled font file has been read. On Web the files arrive over the network,
 * so the shell keeps its loading screen up until this turns true; elsewhere it is true almost at once.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun furyFontsReady(): Boolean {
    val environment = rememberResourceEnvironment()
    val ready by produceState(initialValue = false, environment) {
        listOf(
            Res.font.unbounded_bold, Res.font.unbounded_black,
            Res.font.onest_regular, Res.font.onest_medium, Res.font.onest_semibold, Res.font.onest_bold,
        ).forEach { runCatching { getFontResourceBytes(environment, it) } }
        value = true
    }
    return ready
}
