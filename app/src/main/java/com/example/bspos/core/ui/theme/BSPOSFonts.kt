package com.example.bspos.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.bspos.R

/** Same open-license font faces served by the observed reference; colors remain MiCatalogo's. */
object BSPOSFonts {
    val Default = FontFamily(
        Font(R.font.schibsted_regular, FontWeight.Normal),
        Font(R.font.schibsted_medium, FontWeight.Medium),
        Font(R.font.schibsted_semibold, FontWeight.SemiBold),
        Font(R.font.schibsted_bold, FontWeight.Bold),
        Font(R.font.schibsted_extrabold, FontWeight.ExtraBold)
    )
    val SansSerif = Default
    val Monospace = FontFamily(
        Font(R.font.geist_mono_regular, FontWeight.Normal),
        Font(R.font.geist_mono_medium, FontWeight.Medium),
        Font(R.font.geist_mono_semibold, FontWeight.SemiBold),
        Font(R.font.geist_mono_semibold, FontWeight.Bold)
    )
}
