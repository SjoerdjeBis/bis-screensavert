package nl.bis.screensaver

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** De Bis-huisstijl in de emaille-variant: donker groen, roomwit, tomaat en boter. */
object Bis {
    val Emaille = Color(0xFF0E2E2A)
    val Emaille2 = Color(0xFF16403A)
    val Emaille3 = Color(0xFF1D5049)
    val Room = Color(0xFFF6EFE0)
    val RoomDim = Color(0xFFA9B8B0)
    val Tomaat = Color(0xFFE2523C)
    val Boter = Color(0xFFF0B23F)
    val Rim = Color(0xFF08201D)
    val IJsblauw = Color(0xFF2A5C7A)

    val Fraunces = FontFamily(Font(R.font.fraunces_italic_semibold, FontWeight.SemiBold, FontStyle.Italic))
    val Rubik = FontFamily(
        Font(R.font.rubik_regular, FontWeight.Normal),
        Font(R.font.rubik_medium, FontWeight.Medium),
        Font(R.font.rubik_bold, FontWeight.Bold),
    )

    fun heading(size: TextUnit, color: Color = Room) =
        TextStyle(fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = size, color = color)

    fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Room) =
        TextStyle(fontFamily = Rubik, fontWeight = weight, fontSize = size, color = color, lineHeight = size * 1.35f)

    fun eyebrow(color: Color = Boter) =
        TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.6.sp, color = color)
}

/**
 * Een kaart die je met de afstandsbediening kunt kiezen: bij focus groeit hij iets
 * en krijgt hij een boterkleurige rand, zoals de emaille kopblokken van Bis.
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    background: Color = Bis.Emaille2,
    onFocus: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "schaal")
    val elevation by animateDpAsState(if (focused) 20.dp else 0.dp, label = "schaduw")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(shape)
            .background(background)
            .border(if (focused) 3.dp else 2.dp, if (focused) Bis.Boter else Bis.Rim, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

/** Een kleine, kiesbare pil voor instellingen. */
@Composable
fun FocusPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color? = null) {
    FocusCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50), background = Bis.Emaille2) { focused ->
        Text(
            text,
            style = Bis.body(13.sp, FontWeight.Medium, if (focused) Bis.Room else accent ?: Bis.RoomDim),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** Een label als "ACTIEF" of "AANBEVOLEN", zoals de pillen in Bis Weekmenu. */
@Composable
fun Tag(text: String, color: Color = Bis.Boter, textColor: Color = Color(0xFF20180A), modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(1.5.dp, Bis.Rim, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Bis.eyebrow(textColor).copy(fontWeight = FontWeight.Bold, fontSize = 8.sp))
    }
}

@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text, 600) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(2.dp, Bis.Rim, RoundedCornerShape(16.dp))
            .padding(10.dp),
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = "QR-code")
    }
}

private fun qrBitmap(text: String, px: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 0))
    val pixels = IntArray(px * px) { i -> if (matrix.get(i % px, i / px)) 0xFF12302B.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}
