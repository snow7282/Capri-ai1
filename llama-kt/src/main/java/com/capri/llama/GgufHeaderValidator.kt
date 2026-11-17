
package com.capri.llama

/**
 * Minimal GGUF file-signature check only.
 * This is NOT a GGUF parser or an inference engine.
 */
object GgufHeaderValidator {

    fun hasGgufMagic(header: ByteArray): Boolean =
        header.size >= 4 &&
        header[0] == 'G'.code.toByte() &&
        header[1] == 'G'.code.toByte() &&
        header[2] == 'U'.code.toByte() &&
        header[3] == 'F'.code.toByte()
}
