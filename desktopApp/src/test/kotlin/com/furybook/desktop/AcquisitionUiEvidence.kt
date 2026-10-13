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
    fun envelope(name:String,raw:String) { File(directory,"$name.envelope.json").writeText(raw) }
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
