package com.workflow.operaciones.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Petroleum = Color(0xFF163B43); val Teal = Color(0xFF168F95); val LightTeal = Color(0xFF56B9BC)
    val Selected = Color(0xFFDCEEEE); val Amber = Color(0xFFE8B66A); val Yellow = Color(0xFFF2CD60)
    val Red = Color(0xFFC44747); val Blue = Color(0xFF356EAA); val Green = Color(0xFF2F7D58)
    val Background = Color(0xFFF7F8F6); val SecondaryText = Color(0xFF59656B); val Border = Color(0xFFDFE5E3)
}
@Composable fun WorkflowTheme(content: @Composable () -> Unit) {
    // System font is the offline fallback. Bundle licensed Nunito Sans before final visual approval.
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Brand.Petroleum, onPrimary = Color.White, primaryContainer = Brand.Selected,
            onPrimaryContainer = Brand.Petroleum, secondary = Brand.Teal, background = Brand.Background,
            surface = Color.White, onSurface = Brand.Petroleum, onBackground = Brand.Petroleum,
            onSurfaceVariant = Brand.SecondaryText, outline = Brand.Border, error = Brand.Red),
        typography = Typography(bodyLarge = body, bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 21.sp),
            titleLarge = body.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = body.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            labelLarge = body.copy(fontWeight = FontWeight.SemiBold), labelMedium = body.copy(fontSize = 14.sp)),
        shapes = Shapes(medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)), content = content)
}
