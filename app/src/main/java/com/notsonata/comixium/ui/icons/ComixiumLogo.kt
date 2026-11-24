package com.notsonata.comixium.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notsonata.comixium.ui.theme.LocalComixColors

/**
 * The comix.ws header logo, modified: the wordmark reads COMIXIUM (their I and M reused, a U drawn
 * to match their O). Like on the site, the frame and letters take the theme's text colour and the
 * X mark its accent colour. Source: art/comixium-logo.svg.
 */
object ComixiumLogo {
    const val WIDTH = 700f
    const val HEIGHT = 122.82f
    /** The frame + X emblem occupies x 0..138 of the full logo. */
    const val EMBLEM_WIDTH = 138f

    const val FRAME =
        "M7.36,91.1l7.2-58.02c0-7.3,5.11-13.24,11.4-13.24l82.71-5.98,8.82-8.04c-1.58-.36-3.22-.56-4.9-.56H25.96C12.42,5" +
        ".26,1.41,17.74,1.41,33.08v58.02c0,5.62,1.73,11.7,4.27,16.08,0,0,1.68-15,1.68-16.08ZM126.56,26.08l-1.73,11.08,1" +
        "2.32,8.11v-12.18c0-5.95-1.67-11.47-4.5-16l-6.09,8.99ZM123.99,91.1c0,7.3-5.11,13.24-11.4,13.24l-79.3,11.71-13.2" +
        "5,2.02c1.9.54,3.87.85,5.92.85h86.63c13.54,0,24.55-12.48,24.55-27.83v-12.18h0l-13.15,12.18Z"

    const val WORD =
        "M329.3,35.44l18.56,32.43.62-.58,18.6-31.84h22.95v52.14h-15.38v-36.81l-21.29,36.49c-.2.28-.46.29-.77.33-.97.13-" +
        "9.36.13-9.78-.12l-21.31-36.7v36.48l-.33.33h-15.27v-52.14h23.39ZM245.16,88.03c9.88.45,22.2.46,32.08-.01,10.66-." +
        "51,18.09-3.46,19-15.23.48-6.2.45-16.34,0-22.56-.94-13.17-9.8-15.03-21.18-15.46-9.34-.35-19.9-.24-29.21.24-11.9" +
        "6.61-19.14,2.83-19.9,16.3-.35,6.15-.37,14.69,0,20.83.23,3.9,1.39,8.4,4.26,11.2,3.76,3.67,9.91,4.45,14.96,4.69Z" +
        "M276.84,73.39c-4.01,2.3-18.3,1.98-23.32,1.71-3.42-.19-8.48-.69-10.26-4.01-1.86-3.48-1.77-15.98.08-19.5,1.77-3." +
        "36,7.88-3.71,11.28-3.87,3.96-.18,8.79-.19,12.75-.01,5.15.23,11.13.39,12.52,6.35.69,2.96.63,13.44-.45,16.22-.28" +
        ".72-.94,1.81-1.5,2.34-.25.23-.78.6-1.09.78ZM211.6,37.38c-3.77-2.16-9.34-2.45-13.65-2.6-8.95-.32-21.58-.66-30.2" +
        "9.48-11.7,1.53-15.78,6.64-16.39,18.27-.26,4.99-.27,11.45-.01,16.44.64,12.46,6.78,17.46,19,18.08,9.01.45,20.43." +
        "42,29.45,0,4.92-.23,11.4-1.09,14.93-4.83,3.72-3.93,3.98-10.92,3.4-16h-16.03c.05,3.01-.57,5.87-3.68,6.95-4.1,1." +
        "43-15.5,1.21-20.16.95-3.93-.22-8.46-.8-9.94-4.99-1.14-3.26-1.01-13.73-.16-17.15,1.26-5.03,6.89-5.07,11.19-5.27" +
        "s16.12-.52,19.45,1c2.14.97,2.98,3.13,2.86,5.37h16.03c.16-6.16-.02-13.27-6-16.69ZM425.62,35.44l23.67,25.75-25.4" +
        "3,26.4h20.54l17.16-18.18c.29-.06,2.25,2.02,2.62,2.4,5,5.15,9.64,10.69,14.71,15.78h20.32l-24.81-26.43,22.83-25." +
        "71h-19l-15.15,16.87-1.54,1.31-16.04-18.19h-19.88ZM401.45,35.44v52.14h15.71l.33-.33v-51.82h-16.03ZM508.20,35.44" +
        "v52.14h15.71l.33-.33v-51.82h-16.03ZM533.2,35.44h16.2v29.5c0,7.5,2.6,10.35,10.3,10.35h18.2c7.7,0,10.3-2.85,10.3" +
        "-10.35v-29.5h16.2v31.8c0,15.2-8.5,21.13-24.2,21.13h-22.8c-15.7,0-24.2-5.93-24.2-21.13ZM636.80,35.44l18.56,32.4" +
        "3.62-.58,18.6-31.84h22.95v52.14h-15.38v-36.81l-21.29,36.49c-.2.28-.46.29-.77.33-.97.13-9.36.13-9.78-.12l-21.31" +
        "-36.7v36.48l-.33.33h-15.27v-52.14h23.39Z"

    const val MARK =
        "M86.45,61.66l29.59,34.03-29.48,5-17.4-20.82-38.8,27.3,39.49-38.51,20.8,23.99,9.9-2.23-24.65-28.77L132.12,3.4l-" +
        "45.68,58.26ZM62.66,61.16l-23.18-27.23h11.25l17.99,20.22,30.28-32.03-29.58,20.82-13.67-15.82H22.52l29.58,34.03L" +
        "6.43,119.42l56.23-58.26Z"

    fun vector(text: Color, accent: Color, emblemOnly: Boolean = false): ImageVector {
        val w = if (emblemOnly) EMBLEM_WIDTH else WIDTH
        return ImageVector.Builder("comixium-logo", (w / HEIGHT * 24f).dp, 24.dp, w, HEIGHT).apply {
            addPath(addPathNodes(if (emblemOnly) FRAME else FRAME + WORD), fill = SolidColor(text))
            addPath(addPathNodes(MARK), fill = SolidColor(accent))
        }.build()
    }
}

/** The full logo in the current site theme's colours. */
@Composable
fun ComixiumWordmark(height: Dp, modifier: Modifier = Modifier) {
    val c = LocalComixColors.current
    val vector = remember(c.text, c.accent) { ComixiumLogo.vector(c.text, c.accent) }
    Image(
        imageVector = vector,
        contentDescription = "Comixium",
        modifier = modifier.height(height).aspectRatio(ComixiumLogo.WIDTH / ComixiumLogo.HEIGHT),
    )
}
