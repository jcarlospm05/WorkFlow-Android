package com.workflow.operaciones.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workflow.operaciones.R

object Brand {
    val Petroleum = Color(0xFF163B43); val Teal = Color(0xFF168F95); val LightTeal = Color(0xFF56B9BC)
    val Selected = Color(0xFFDCEEEE); val Amber = Color(0xFFE8B66A); val Yellow = Color(0xFFF2CD60)
    val Red = Color(0xFFC44747); val Blue = Color(0xFF356EAA); val Green = Color(0xFF2F7D58)
    val Background = Color(0xFFF7F8F6); val SecondaryText = Color(0xFF59656B); val Border = Color(0xFFDFE5E3)
    val Sage = Color(0xFFE7EFE3); val Sky = Color(0xFFE2EEF3); val Sand = Color(0xFFF2EEDF)
    val Nunito = FontFamily(Font(R.font.nunito_regular, FontWeight.Normal), Font(R.font.nunito_medium, FontWeight.Medium), Font(R.font.nunito_semibold, FontWeight.SemiBold))
}
@Composable fun WorkflowTheme(content: @Composable () -> Unit) {
    val body = TextStyle(fontFamily = Brand.Nunito, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal)
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Brand.Petroleum, onPrimary = Color.White, primaryContainer = Brand.Selected,
            onPrimaryContainer = Brand.Petroleum, secondary = Brand.Teal, background = Brand.Background,
            surface = Color.White, surfaceContainer = Brand.Background, onSurface = Brand.Petroleum, onBackground = Brand.Petroleum,
            onSurfaceVariant = Brand.SecondaryText, outline = Brand.Border, error = Brand.Red),
        typography = Typography(
            headlineLarge = body.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = body.copy(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = body.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = body.copy(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = body, bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp), bodySmall = body.copy(fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = body.copy(fontWeight = FontWeight.SemiBold), labelMedium = body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            labelSmall = body.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)),
        shapes = Shapes(extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(26.dp)), content = content)
}
