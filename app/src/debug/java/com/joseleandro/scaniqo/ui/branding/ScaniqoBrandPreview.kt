package com.joseleandro.scaniqo.ui.branding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.joseleandro.scaniqo.R

/** Load the actual launcher resources using the installed preview runtime. */
@Preview(name = "Native launcher", apiLevel = 34, widthDp = 280, heightDp = 160, showBackground = true)
@Composable
private fun NativeLauncherPreview() {
    val context = LocalContext.current
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round).forEach { resource ->
            val drawable = context.getDrawable(resource)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(drawable?.javaClass?.simpleName.orEmpty(), fontSize = 10.sp)
                AndroidView(
                    factory = { android.widget.ImageView(it) },
                    update = { it.setImageDrawable(drawable) },
                    modifier = Modifier.size(100.dp),
                )
            }
        }
    }
}

/** Preview-only brand sheet; uses the actual launcher layers at the Android mask scale. */
@Preview(name = "Scaniqo brand", widthDp = 420, heightDp = 568, showBackground = true)
@Composable
private fun ScaniqoBrandPreview() {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F9F8)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        BrandIcon(144.dp, RoundedCornerShape(38.dp))
        Text("Scaniqo", color = Color(0xFF06364A), fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandIcon(64.dp, CircleShape)
            BrandIcon(64.dp, RoundedCornerShape(18.dp))
            BrandIcon(64.dp, CircleShape, monochrome = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandIcon(48.dp, RoundedCornerShape(14.dp))
            BrandIcon(32.dp, RoundedCornerShape(9.dp))
            BrandIcon(24.dp, RoundedCornerShape(7.dp))
        }
        Row(
            Modifier.background(Color(0xFF101719), RoundedCornerShape(24.dp)).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BrandIcon(48.dp, CircleShape)
            BrandIcon(48.dp, RoundedCornerShape(14.dp), monochrome = true, dark = true)
        }
    }
}

@Composable
private fun BrandIcon(size: Dp, shape: Shape, monochrome: Boolean = false, dark: Boolean = false) {
    val background = if (!monochrome) Color(0xFF06364A) else if (dark) Color(0xFF12382F) else Color(0xFFC0ECE1)
    val tint = if (dark) Color(0xFFC0ECE1) else Color(0xFF12382F)
    Box(Modifier.size(size).clip(shape).background(background)) {
        Image(
            painterResource(if (monochrome) R.drawable.ic_launcher_monochrome else R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = if (monochrome) ColorFilter.tint(tint) else null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
        )
    }
}
