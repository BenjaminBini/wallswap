package com.benjaminbini.wallswap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Darkens the bottom of an image so white text on it stays readable. */
@Composable
fun BottomScrim(modifier: Modifier = Modifier, fraction: Float = 0.55f) {
    Box(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
    )
}

/** "Photo by <photographer> on Unsplash", both linked as the Unsplash guidelines require. */
@Composable
fun Credit(photo: Photo, color: Color, modifier: Modifier = Modifier) {
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        append("Photo by ")
        withLink(LinkAnnotation.Url(photo.photographerUrl, linkStyle)) { append(photo.photographer) }
        append(" on ")
        withLink(LinkAnnotation.Url(photo.pageUrl, linkStyle)) { append("Unsplash") }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}
