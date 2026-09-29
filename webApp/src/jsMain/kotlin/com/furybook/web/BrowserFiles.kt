@file:Suppress("UnsafeCastFromDynamic")

package com.furybook.web

import kotlinx.browser.document

fun pickTextFile(accept: String, onResult: (String) -> Unit) {
    val input = document.createElement("input").asDynamic()
    input.type = "file"
    input.accept = accept
    input.onchange = {
        val file = input.files?.item(0)
        if (file != null) {
            val reader = js("new FileReader()")
            reader.onload = { onResult(reader.result as String) }
            reader.readAsText(file)
        }
        null
    }
    input.click()
}

fun pickPortraitDataUrl(onResult: (String) -> Unit) {
    val input = document.createElement("input").asDynamic()
    input.type = "file"
    input.accept = "image/png,image/jpeg,image/webp"
    input.onchange = {
        val file = input.files?.item(0)
        if (file != null) {
            val reader = js("new FileReader()")
            reader.onload = { onResult(reader.result as String) }
            reader.readAsDataURL(file)
        }
        null
    }
    input.click()
}

fun downloadTextFile(fileName: String, text: String) {
    val parts = arrayOf(text)
    val options = js("({})")
    options.type = "application/json;charset=utf-8"
    val blob = js("new Blob(parts, options)")
    val url = js("URL.createObjectURL(blob)") as String
    val anchor = document.createElement("a").asDynamic()
    anchor.href = url
    anchor.download = fileName
    document.body?.appendChild(anchor)
    anchor.click()
    anchor.remove()
    js("URL.revokeObjectURL(url)")
}

fun dataUrlBytes(dataUrl: String): ByteArray {
    val encoded = dataUrl.substringAfter(',', "")
    if (encoded.isBlank()) return ByteArray(0)
    val binary = js("atob(encoded)") as String
    return ByteArray(binary.length) { index -> binary[index].code.toByte() }
}
