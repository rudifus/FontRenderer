package com.rudolas.mia.fontrenderer

import android.graphics.Bitmap
import android.graphics.Color
import java.io.IOException
import java.util.ArrayList

class FontPreview {

    val previewMapBuilder = Array<ArrayList<Int>>(ASCII_LATIN_COUNT) { arrayListOf() }
    val widthsArray = IntArray(ASCII_LATIN_COUNT)
    var overlayBitmap: Bitmap? = null

    /**
     * show message for selected font - compacted to fit the display screen
     *
     * @param message  demo text
     * @throws IOException
     */
    fun renderGraphicsMessageCompacted3(message: String = MESSAGE, rowOffset: Int = 0) {
        var charIndex = -1
        var rowIndex = rowOffset
        var lastRowEndCharIndex = -1
        val messageLength = message.length
        val width = getScreenWidth()
        val height = getScreenHeight()

        clearBitmap()
        val widths = ArrayList<Int>(20)
        while (charIndex < messageLength - 1 && rowIndex <= height) {
            var pixelCount = 0

            widths.clear()
            while (charIndex < messageLength - 1 && pixelCount < width) {
                val nextCharIndex = charIndex + 1
                val charValue = message[nextCharIndex]
                val charPixelsWidth = getFontDataWidth(charValue)
                widths.add(charPixelsWidth)
//                logMsg(
//                    "pixels[$rowIndex, $nextCharIndex] '$charValue' dataIndex ${charToFontIndex(charValue)}" +
//                            ' '.toString() + pixelCount + " Pixels " + charPixelsWidth
//                )
                if (pixelCount + charPixelsWidth > width) {
                    break
                }
                pixelCount += charPixelsWidth
                charIndex++
            }

            val rowSize = charIndex - lastRowEndCharIndex
            val startIndex = lastRowEndCharIndex + 1
            lastRowEndCharIndex = charIndex
            val charArray = message.substring(startIndex, startIndex + rowSize).toCharArray()

//            logMsg("row[$rowIndex, $charIndex]  chars $rowSize")

            val charsDataList = ArrayList<IntArray>(rowSize)
            for (i in 0 until rowSize) {
                charsDataList.add(getFontData(charArray[i], FONT_DATA))
            }

            val charsData = charsDataList.toTypedArray()
            if (charsData.isNotEmpty()) {
                val rowCharWidths = widths.toTypedArray()
                val charDataHeight = charsData[0].size
                logMsg("row[" + rowIndex + ", " + startIndex + "] " + rowSize + " chars Height " + charDataHeight + "px")

                // i is row index within char
                for (i in 0 until charDataHeight) {
                    val actRowIndex = rowIndex + i
                    if (actRowIndex >= height) {
                        break
                    }

                    var pos = 0
                    val rowPixelFlags = BooleanArray(width)
                    for (rowCharIndex in charsData.indices) {
                        val charData = charsData[rowCharIndex]
                        if (charData.isNotEmpty()) {
                            val fontChar = charData[i]
                            val charDataWidth = rowCharWidths[rowCharIndex]

                            for (k in 0 until charDataWidth) {
                                rowPixelFlags[pos + k] = 1 shl charDataWidth - k - 1 and fontChar != 0
                            }

                            pos += charDataWidth
                        }
                    }
                    setRowPixelArray(0, actRowIndex, rowPixelFlags)
                }
                rowIndex += charDataHeight
            }
        }
    }

    /**
     * clear preview bitmap
     */
    private fun clearBitmap() {
        val width = overlayBitmap?.width ?: -1
        val height = overlayBitmap?.height ?: -1
        logMsg("clearBitmap [$width, $height]")
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("pixels out of bound: w:h[$width, $height]")
        }
        val pixels = IntArray(width * height) { COLOR_SCREEN_BLUE }
        overlayBitmap?.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * set row pixels of bitmap
     */
    private fun setRowPixelArray(x: Int, y: Int, onArray: BooleanArray) {
        val arrayLength = onArray.size
//        logMsg("setRowPixels [$x, $y] $arrayLength")
        if (arrayLength == 0) {
            throw IllegalArgumentException("empty pixel array not allowed")
        }
        val width = getScreenWidth()
        val height = getScreenHeight()
        if (x < 0 || y < 0 || width < 0 || height < 0 || x >= width || y >= height) {
            throw IllegalArgumentException("pixel out of bound:$x,$y  w:h[$width, $height]")
        }

        for (i in 0 until arrayLength) {
            val value = if (onArray[i]) Color.WHITE else COLOR_SCREEN_BLUE
            overlayBitmap?.setPixel(IMAGE_OFFSET + x + i, IMAGE_OFFSET + y, value)
        }
    }

    private fun getScreenHeight() = (overlayBitmap?.height ?: -1) - 2 * IMAGE_OFFSET

    private fun getScreenWidth() = (overlayBitmap?.width ?: -1) - 2 * IMAGE_OFFSET

    private fun getFontDataWidth(charValue: Char): Int = widthsArray[charToFontIndex(charValue)]

    private fun getFontData(charValue: Char, dataType: Int): IntArray {
        return if (dataType == FONT_DATA_WIDTH) {
            intArrayOf(widthsArray[charToFontIndex(charValue)])
        } else {
            previewMapBuilder[charToFontIndex(charValue)].toIntArray()
        }
    }

    fun createImageBitmap(multiplier: Int): Bitmap {
        overlayBitmap = Bitmap.createBitmap(
            multiplier * 128 + 2 * IMAGE_OFFSET,
            multiplier * 96 + 2 * IMAGE_OFFSET,
            Bitmap.Config.ARGB_8888
        )
        logMsg("CREATED BITMAP ${multiplier * 128} x ${multiplier * 64}")
        return overlayBitmap!!
    }

    companion object {

        const val IMAGE_OFFSET: Int = 8
        private val COLOR_SCREEN_BLUE: Int = Color.rgb(64, 32, 255)
        private const val FONT_DATA = 0
        private const val FONT_DATA_WIDTH = 1
        private const val MESSAGE =
            "Žiadny príklad by nebol krajší, než takýto krásny text s dĺžňami a mäkčeňmi rýdzo po slovensky."

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

        private fun charToFontIndex(charValue: Char): Int {
            return if (charValue.toInt() > 130) charValue.toInt() - 66 else charValue.toInt() - 32
        }

        private fun logMsg(msg: String) = android.util.Log.d("FontPreview", msg)
    }
}