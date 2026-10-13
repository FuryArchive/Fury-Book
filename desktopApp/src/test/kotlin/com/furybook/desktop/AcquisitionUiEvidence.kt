package com.furybook.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.security.MessageDigest

internal object AcquisitionUiEvidence {
    private val directory get()=File(System.getProperty("furybook.acquisition.ui.evidence")).also { it.mkdirs() }
    fun screenshot(name:String,bitmap:ImageBitmap) {
        val bytes=Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { it.encodeToData(EncodedImageFormat.PNG)!!.use { data -> data.bytes } }
        File(directory,"$name.png").writeBytes(bytes)
        File(directory,"screenshots.tsv").appendText("$name\t${bitmap.width}\t${bitmap.height}\t${hash(bytes)}\n")
        println("T18B_RENDERED_PNG $name ${bitmap.width}x${bitmap.height} sha256=${hash(bytes)}")
    }
    /** Native captured pixels, not only laid-out semantics. No image postprocessing. */
    fun assertOpaqueGlyphs(bitmap: ImageBitmap, regions: List<androidx.compose.ui.geometry.Rect>): Long {
        val pixels=bitmap.asSkiaBitmap()
        for((x,y) in listOf(0 to 0,(bitmap.width-1) to 0,0 to (bitmap.height-1),(bitmap.width-1) to (bitmap.height-1))) {
            check((pixels.getColor(x,y) ushr 24)==255) { "Native fixture lacks its opaque production surface at $x,$y" }
        }
        var fingerprint=1L
        for((index,rect) in regions.withIndex()) {
            val left=rect.left.toInt().coerceIn(0,bitmap.width-1);val top=rect.top.toInt().coerceIn(0,bitmap.height-1)
            val right=rect.right.toInt().coerceIn(left+1,bitmap.width);val bottom=rect.bottom.toInt().coerceIn(top+1,bitmap.height)
            val background=pixels.getColor(left,top);var contrasting=0
            for(y in top until bottom step 2) for(x in left until right step 2) {
                val color=pixels.getColor(x,y);check((color ushr 24)==255) { "Transparent rendered text region $index" }
                if(color!=background) contrasting++
                fingerprint=31*fingerprint+color
            }
            check(contrasting>=3) { "Native text region $index has no drawn contrasting glyph pixels" }
        }
        return fingerprint
    }
    fun envelope(name:String,raw:String) { File(directory,"$name.envelope.json").writeText(raw) }
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
