package com.rudolas.mia.fontrenderer

import android.graphics.Bitmap
import android.graphics.Color
import java.util.ArrayList
import kotlin.math.max

data class FontPreview(
    internal var pixelWidthMax: Int = 0,
    internal var pixelHeightMax: Int = 0,
    internal var pixels: Array<Array<Boolean>> = emptyArray(),
    internal val arrayNameCamel: String
) {
    internal val dims = Array(ASCII_LATIN_COUNT) { "" }
    internal val previewMapBuilder = Array<ArrayList<Int>>(ASCII_LATIN_COUNT) { arrayListOf() }
    internal val widthsArray = IntArray(ASCII_LATIN_COUNT)
    internal var bitmap: Bitmap? = null
    internal var bitmap2: Bitmap? = null

    internal fun initialize(
        multiplier: Int,
        fontSizeDim: Int
    ): FontPreview {
        bitmap = Bitmap.createBitmap(
            multiplier * 128 + 2 * IMAGE_OFFSET,
            multiplier * 96 + 2 * IMAGE_OFFSET,
            Bitmap.Config.ARGB_8888
        )
        bitmap2 = Bitmap.createBitmap(
            multiplier * 128 + 2 * IMAGE_OFFSET,
            multiplier * 64 + 2 * IMAGE_OFFSET,
            Bitmap.Config.ARGB_8888
        )
//        logMsg("CREATED $arrayNameCamel FontPreview BITMAP ${multiplier * 128} x ${multiplier * 96} ")
        pixels = Array(fontSizeDim) { Array(fontSizeDim) { false } }

        return this
    }

    /**
     * clear preview bitmap
     */
    internal fun clearBitmap(useLarge: Boolean) {
        clearBitmap (if (useLarge) bitmap else bitmap2)
    }

    private fun clearBitmap(previewBitmap: Bitmap?) {
        val width = previewBitmap?.width ?: -1
        val height = previewBitmap?.height ?: -1
//        logMsg("clearBitmap [$width, $height]")
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("pixels out of bound: w:h[$width, $height]")
        }
        val pixels = IntArray(width * height) { COLOR_SCREEN_BLUE }
        previewBitmap?.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * set row pixels of bitmap
     */
    internal fun setRowPixelArray(x: Int, y: Int, onArray: BooleanArray, useLarge: Boolean) {
        val arrayLength = onArray.size
//        logMsg("setRowPixels [$x, $y] $arrayLength")
        if (arrayLength == 0) {
            throw IllegalArgumentException("empty pixel array not allowed")
        }
        val width = getScreenWidth(useLarge)
        val height = getScreenHeight(useLarge)
        if (x < 0 || y < 0 || width < 0 || height < 0 || x >= width || y >= height) {
            throw IllegalArgumentException("pixel out of bound:$x,$y  w:h[$width, $height]")
        }
        val previewBitmap = if (useLarge) bitmap else bitmap2
        for (i in 0 until arrayLength) {
            val value = if (onArray[i]) Color.WHITE else COLOR_SCREEN_BLUE
            previewBitmap?.setPixel(IMAGE_OFFSET + x + i, IMAGE_OFFSET + y, value)
        }
    }

    internal fun getScreenHeight(useLarge: Boolean) = ((if (useLarge) bitmap else bitmap2)?.height ?: -1) - 2 * IMAGE_OFFSET

    internal fun getScreenWidth(useLarge: Boolean) = ((if (useLarge) bitmap else bitmap2)?.width ?: -1) - 2 * IMAGE_OFFSET

    internal fun getFontDataWidth(charValue: Char): Int = widthsArray[charToFontIndex(charValue)]

    internal fun getFontData(charValue: Char): IntArray = previewMapBuilder[charToFontIndex(charValue)].toIntArray()

    fun updatePixelSizes(bitmapWidth: Int, bitmapHeight: Int) {
        pixelHeightMax = max(bitmapHeight, pixelHeightMax)
        pixelWidthMax = max(bitmapWidth, pixelWidthMax)
        //                            logMsg("SK: Max bitmap $pixelWidthMax x $pixelHeightMax '${fontCharTextView.text}'")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FontPreview

        if (pixelWidthMax != other.pixelWidthMax) return false
        if (pixelHeightMax != other.pixelHeightMax) return false
        if (!pixels.contentDeepEquals(other.pixels)) return false
        if (arrayNameCamel != other.arrayNameCamel) return false
        if (!dims.contentEquals(other.dims)) return false
        if (!previewMapBuilder.contentEquals(other.previewMapBuilder)) return false
        if (!widthsArray.contentEquals(other.widthsArray)) return false
        if (bitmap != other.bitmap) return false
        if (bitmap2 != other.bitmap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pixelWidthMax
        result = 31 * result + pixelHeightMax
        result = 31 * result + pixels.contentDeepHashCode()
        result = 31 * result + arrayNameCamel.hashCode()
        result = 31 * result + dims.contentHashCode()
        result = 31 * result + previewMapBuilder.contentHashCode()
        result = 31 * result + widthsArray.contentHashCode()
        result = 31 * result + (bitmap?.hashCode() ?: 0)
        result = 31 * result + (bitmap2?.hashCode() ?: 0)
        return result
    }

    fun clear() {
        bitmap = null
        bitmap2?.recycle()
        bitmap2 = null
    }

    companion object {

        const val IMAGE_OFFSET: Int = 8
        private val COLOR_SCREEN_BLUE: Int = Color.rgb(64, 32, 255)

        /**
         * supported basic ascii characters
         */
        internal val ASCII_LATIN_RANGE1 = 32..126    // space .. ~
        /**
         * supported extended latin ascii chars
         * optionally additional greek and coptic, armenian, cyrillic, hebrew or arabic ASCII subsets could be enabled
         */
        internal val ASCII_LATIN_RANGE2 = 161..382 // ¡ .. À .. ž

        internal val ASCII_LATIN_COUNT =
            ASCII_LATIN_RANGE1.last - ASCII_LATIN_RANGE1.first + ASCII_LATIN_RANGE2.last - ASCII_LATIN_RANGE2.first + 2

        internal fun charToFontIndex(charValue: Char): Int {
            return if (charValue.toInt() > 130) charValue.toInt() - 66 else charValue.toInt() - 32
        }

        private fun logMsg(msg: String) = android.util.Log.d("FontPreview", msg)
    }
}