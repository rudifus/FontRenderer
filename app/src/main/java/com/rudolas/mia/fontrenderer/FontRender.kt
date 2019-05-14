package com.rudolas.mia.fontrenderer

import android.graphics.Bitmap
import android.os.Environment.getExternalStorageDirectory
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

class FontRender {
    private val fontPreviewArray = arrayOfNulls<FontPreview>(getFontsCount())
    private val latinCharacters = getExtendedLatinCharactersString()

    fun processCharacterBitmap(
        fontIndex: Int,
        charIndex: Int,
        charBitmap: Bitmap,
//        toGenerateCharPixelsPreview: Boolean,
        updatePreviewBitmap: (Bitmap) -> Unit
    ) {
        val fontParams = getFontParams(fontIndex)

        if (charIndex < 1 || charIndex > 315) {
            logMsg("SK: Render START [$fontIndex] ${fontParams.fontName} ${fontParams.fontSize.toInt()}px [$charIndex] '${latinCharacters[charIndex]}'")
        }
        if (charIndex == 0) {
            val fontSize = fontParams.fontSize.toInt()
            val nameParts = fontParams.fontName.split('_')
            val arrayNameCamel =
                "${Array(nameParts.size) { nameParts[it].capitalize() }.joinToString("")}${fontSize}px"
//            logMsg("SK: Render File [$fontIndex] $arrayNameCamel $fontSize}px [$charIndex] '$charText'")
            fontPreviewArray[fontIndex] = FontPreview(arrayNameCamel = arrayNameCamel)
                .initialize(1, fontSize * 3)
        }

        val fontPreview = fontPreviewArray[fontIndex]!!
        val pixelsSize = fontPreview.pixels.size
        val divider = fontParams.divider
        val bitmapHeight =
            if (charBitmap.height - fontParams.bottomOffset >= pixelsSize) 1 else (charBitmap.height - fontParams.bottomOffset) / divider

        val bitmapWidth = if (charBitmap.width >= pixelsSize) 1 else charBitmap.width / divider
        val topOffset = fontParams.topOffset
        fontPreview.dims[charIndex] = "${bitmapWidth}x${bitmapHeight - topOffset / divider}"
        fontPreview.widthsArray[charIndex] = bitmapWidth

        if (bitmapHeight * divider < pixelsSize && bitmapWidth * divider < pixelsSize) {
            fontPreview.updatePixelSizes(bitmapWidth, bitmapHeight)
        }
        val fontMapBuilder = fontPreview.previewMapBuilder[charIndex]
        fontMapBuilder.clear()
        for (y in topOffset until bitmapHeight * divider step divider) {
            val pixelY = y / divider
            val pixelsHeats = fontPreview.pixels[pixelY]
            var byte = 0
            for (x in 0 until bitmapWidth * divider step divider) {
                val isPixelOn = charBitmap.getPixel(x, y) != 0
                if (isPixelOn) {
                    val pixelX = x / divider
                    if (pixelX < pixelsSize && pixelY < pixelsSize && !pixelsHeats[pixelX]) {
                        pixelsHeats[pixelX] = true
                    }
                    byte += 1 shl (bitmapWidth - x / divider - 1)
                }
//                if (toGenerateCharPixelsPreview) {
//                    stringPreviewBuilder.append(if (isPixelOn) 1 else 0)
//                }
            }
            fontMapBuilder.add(byte)
        }
        charBitmap.recycle() // recycle manually if not assigned to imageView

        if (charIndex + 1 >= FontPreview.ASCII_LATIN_COUNT) {
//            logMsg("SK: Render [$fontIndex] $fontName ${fontParams.fontSize.toInt()}px ${latinCharacters[charIndex]}")
            // init kotlin /java files
            val arrayNameCamel = fontPreview.arrayNameCamel
            val fontName = fontParams.fontName
            val fontSize = fontParams.fontSize
            val stringFileBuilders = Array(2) { StringBuilder(32000) }
            appendFontFile(
                stringFileBuilders,
                "package fonts;\n" +
                        "\npublic class $arrayNameCamel {" +
                        "\n    private  static final int[][] charsPixels = {",
                "package fonts\n" +  //com.rudolas.mia.lcdst7920.fonts
                        "\nclass $arrayNameCamel {\n" +
                        "    companion object {\n" +
                        "        val font = FontItem( // FONT ${fontSize}px $fontName.ttf\n" +
                        "            \"${"${fontName.toUpperCase(Locale.ROOT)}_${fontSize}PX"}\",\n" +
                        "            charBytes = arrayOf("
            )
            // appends font chars map rendering as FontItem FONT object
            val fontPreviewBytesArray = fontPreview.previewMapBuilder
            val stringPreviewBuilder = StringBuilder(40)
            val stringHexBuilder = StringBuilder(40)
            for ((index, fontPreviewBytes) in fontPreviewBytesArray.withIndex()) {
                val fontCharBytes = fontPreviewBytes.toIntArray()
                for ((indexY, byte) in fontCharBytes.withIndex()) {

//            if (toGenerateCharPixelsPreview) {
//                appendFontFile("// $stringPreviewBuilder [$y]") // binary char pixels preview
//                stringPreviewBuilder.clear()
//            }
                    appendHexString(stringHexBuilder.append(if (indexY == 0 || indexY == fontCharBytes.size) "" else ", ").apply {
                        if (indexY > 0 && indexY % HEX_VALUES_LINE_LIMIT == 0) {
                            stringHexBuilder.append("\n ")
                        }
                    }, byte)
                }
                val rowBytes = stringHexBuilder.toString()
                stringHexBuilder.clear()
                if (bitmapHeight < HEX_VALUES_LINE_LIMIT) {
                    for (j in 0..65 - rowBytes.length) {
                        stringHexBuilder.append(' ')
                    }
                }
                val isNotLastChar = index < FontPreview.ASCII_LATIN_COUNT - 1
                if (!isNotLastChar) {
                    stringHexBuilder.append(' ')
                }
                val spacer = stringHexBuilder.toString()

                stringHexBuilder.clear()

                val charText = latinCharacters[index]
                appendHexString(stringHexBuilder, charText.toInt())
                val hexAscii = stringHexBuilder.toString()
                stringHexBuilder.clear()

                if (bitmapHeight <= 1) {
                    val appendix = ", // blank font char '$charText' $hexAscii"
                    appendFontFile(
                        stringFileBuilders,
                        "            {}$appendix",
                        "                IntArray(0)$appendix"
                    )
                } else {
                    val appendix = "${if (isNotLastChar) "," else ""
                    }$spacer // [$index] ${fontPreview.dims[index]} '$charText' $hexAscii"
                    appendFontFile(
                        stringFileBuilders,
                        "            {$rowBytes}$appendix",
                        "                intArrayOf($rowBytes)$appendix"
                    )
                }
            }

            appendFontFile(
                stringFileBuilders,
                "    };\n    private static final int[] widths = {",
                "            ),\n            widths = intArrayOf("
            )

            val widthsArray = fontPreview.widthsArray

            // alter first ' ' blank width space to render compacted sentences width on display
            widthsArray[0] = min(widthsArray[0], widthsArray[FontPreview.charToFontIndex('a')] / 2 + 1)

            for (i in widthsArray.indices) {
                stringPreviewBuilder.append(widthsArray[i])
                    .append(if (i < FontPreview.ASCII_LATIN_COUNT - 1) "," else "")
                if (i % 10 == 9) {
                    for (j in 0..29 - stringPreviewBuilder.length) {
                        stringPreviewBuilder.append(' ')
                    }
                    val firstChar = latinCharacters[i - 9]
                    appendHexString(stringHexBuilder, firstChar.toInt())
                    val appendix =
                        "$stringPreviewBuilder // '$firstChar'..'${latinCharacters[i]}' $stringHexBuilder"
                    appendFontFile(
                        stringFileBuilders,"            $appendix", "                $appendix"
                    )
                    stringPreviewBuilder.clear()
                    stringHexBuilder.clear()
                }
            }
            if (stringPreviewBuilder.isNotEmpty()) {
                for (j in 0..29 - stringPreviewBuilder.length) {
                    stringPreviewBuilder.append(' ')
                }
                val appendix =
                    "$stringPreviewBuilder //    ..'${latinCharacters[widthsArray.indices.last]}'"
                appendFontFile(
                    stringFileBuilders,"            $appendix", "                $appendix"
                )
                stringPreviewBuilder.clear()
            }

            // append font widths array
            appendFontFile(
                stringFileBuilders,
                "    };\n\n    public static final FontItem FONT = new FontItem(\n" +
                        "            \"${fontParams.fontName.toUpperCase(Locale.ROOT)}_${fontParams.fontSize.toInt()}PX\",\n" +
                        "            charsPixels,\n" +
                        "            widths\n" +
                        "    );\n}",
                "            )\n        )\n    }\n}"
            )

            val pixelHeightMax = fontPreview.pixelHeightMax
            val pixelWidthMax = fontPreview.pixelWidthMax
            appendFontFile(
                stringFileBuilders,
                "// Max Character Bitmap $pixelWidthMax x $pixelHeightMax ${
                if (fontParams.divider > 1) "Divider ${fontParams.divider}" else ""
                } Offsets [Top=${fontParams.topOffset}, Bottom=${fontParams.bottomOffset}]" +
                        "\n// Mass Matrix - merged text preview from all characters rendered into one map"
            )
//            val fontPreview = fontPreviewArray[fontIndex]!!
//            val pixelsSize = fontPreview.pixels.size
            for (y in 0 until min(pixelHeightMax, pixelsSize)) {
                val pixels = fontPreview.pixels[y]
                for (x in 0 until min(pixelWidthMax, pixelsSize)) {
                    stringPreviewBuilder.append(if (pixels[x]) '#' else '.')  // preview '#' as pixel on and '.' as an empty pixel
                }
                appendFontFile(
                    stringFileBuilders,"// Mass Matrix $stringPreviewBuilder $y"
                )
                stringPreviewBuilder.clear()
            }

            val downloadDir = File(getExternalStorageDirectory(), "Download")
            val fontsDir = File(downloadDir, "Fonts")
            if (!fontsDir.exists() && !fontsDir.mkdir()) {
                logMsg("CANNOT ACCESS Fonts directory")
                //                                    return
            }
            // Java, Kotlin code output files
            val fontFiles = Array(2) {
                File(fontsDir, "$arrayNameCamel.${getLangPrefix(it)}").apply {
                    if (!exists()) {
                        logMsg("Font file to be created $absolutePath")
                    }
                }
            }
            val fontBitmapsDir = File(downloadDir, "FontBitmaps")
            if (!fontBitmapsDir.exists() && !fontBitmapsDir.mkdir()) {
                logMsg("CANNOT ACCESS FontBitmaps directory")
                //                                    return
            }
            // 3x large 128x96 and 2x 126x64 preview bitmaps
            val fontBitmapFiles = Array(5) {
                File(fontBitmapsDir, "${arrayNameCamel}_$it.webp")
            }
            // write file content builder to output file
            for (i in fontFiles.indices) {
                fontFiles[i].sink(false).buffer().use {
                    it.write(stringFileBuilders[i].toString().encodeUtf8())
                }
                stringFileBuilders[i].clear()
            }

            if (fontPreview.bitmap != null) {
                // render the 1st font message large preview 128x96 bitmap and show it in image view
                renderGraphicsMessageCompacted3(fontPreview, skLatinChars)
                updatePreviewBitmap(fontPreview.bitmap!!)
                writePreviewBitmapFile(fontBitmapFiles[0], fontPreview.bitmap!!)

                // render the 2nd font message large preview 128x96 bitmap
                renderGraphicsMessageCompacted3(fontPreview)
                writePreviewBitmapFile(fontBitmapFiles[1], fontPreview.bitmap!!)

                // render the 3rd font message smaller preview 128x64 bitmap
                renderGraphicsMessageCompacted3(fontPreview, useLarge = false)
                writePreviewBitmapFile(fontBitmapFiles[2], fontPreview.bitmap2!!)

                // render the 4th font message smaller preview 128x64 bitmap
                renderGraphicsMessageCompacted3(fontPreview, message = enLatinChars, useLarge = false)
                writePreviewBitmapFile(fontBitmapFiles[3], fontPreview.bitmap2!!)

                // render the 5th font message smaller preview 128x96 bitmap
                renderGraphicsMessageCompacted3(fontPreview, message = enLatinChars)
                writePreviewBitmapFile(fontBitmapFiles[4], fontPreview.bitmap!!)
            }
            // recycle bitmaps, remove fontPreview object from array
            fontPreview.clear()
            fontPreviewArray[fontIndex] = null
        }
    }

    private fun writePreviewBitmapFile(fontBitmapFile: File, previewBitmap: Bitmap) {
        val scaledBitmap =
            Bitmap.createScaledBitmap(previewBitmap, previewBitmap.width * 2, previewBitmap.height * 2, true)
        fontBitmapFile.sink(false).buffer().use {
            ByteArrayOutputStream(8000).use { outStream ->
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 100, outStream)
                it.write(outStream.toByteArray())
            }
        }
        scaledBitmap.recycle()
    }

    /**
     * appends hex form of provided ascii char into a given string builder
     */
    private fun appendHexString(stringBuilder: StringBuilder, ascii: Int) {
        val hexString = Integer.toHexString(ascii).toUpperCase(Locale.ROOT)
        stringBuilder.append("0x")
            .append(if (hexString.length == 1) "0" else "")
            .append(hexString)
    }

    /**
     * provides target lang prefix, file extension
     */
    private fun getLangPrefix(targetLang: Int): String {
        return when (targetLang) {
            LANG_JAVA -> "java"
            LANG_PYTHON -> "py"
            LANG_C -> "c"
            else -> "kt"
        }
    }

    private fun appendFontFile(stringFileBuilders: Array<StringBuilder>, line: String) {
        appendFontFile(stringFileBuilders, line, line)
    }

    /**
     * appends string line to output file string builder - to speed up file generation.
     * It will be flushed to physical sdcard file at the end of rendering
     */
    private fun appendFontFile(stringFileBuilders: Array<StringBuilder>, lineJava: String, lineKotlin: String) {
        stringFileBuilders[LANG_JAVA].append(lineJava).append("\n")
        stringFileBuilders[LANG_KOTLIN].append(lineKotlin).append("\n")
//                        logMsg("SK: $line")
    }

    /**
     * creates characters array containing:
     * 1.) basic alphabets and signs ASCII[32..126]
     *   !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~
     *
     * 2.) extended latin characters ASCII[161..382]
     *   ¡¢£¤¥¦§¨©ª«¬­®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ
     *   ĀāĂăĄąĆćĈĉĊċČčĎďĐđĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħĨĩĪīĬĭĮįİıĲĳĴĵĶķĸĹĺĻļĽľĿŀŁłŃńŅņŇňŉŊŋŌōŎŏŐőŒœ
     *   ŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŧŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽž
     */
    fun getExtendedLatinCharactersString(): String {
        val strBuilder = StringBuilder(FontPreview.ASCII_LATIN_COUNT)
        for (int in FontPreview.ASCII_LATIN_RANGE1 + FontPreview.ASCII_LATIN_RANGE2) {
            strBuilder.append(int.toChar())
        }
        return strBuilder.toString()
    }

    /**
     * get declared native font size to use for rendering
     */
    fun getFontParams(fontIndex: Int) = FONT_PARAMS[fontIndex]

    fun getFontsCount(): Int = FONT_PARAMS.size

    fun getFontParamsList() =
        FONT_PARAMS.mapIndexed { index, fontParams -> "$index. ${fontParams.fontName.replace('_', ' ')} ${fontParams.fontSize.toInt()}px" }.toMutableList()

    /**
     * rendering related data for each true type font found within res/font
     * In case of adding a new true type font resource, an instance of this object is recommended to be add into FONT_PARAMS
     * to enable font rendering and preview
     */
    data class FontParams(
        internal var divider: Int = 1,
        internal var topOffset: Int = 0,
        internal var bottomOffset: Int = 0,
        internal var fontSize: Float,
        internal var fontRes: Int,
        internal var fontName: String
    )

    companion object {

        private const val HEX_VALUES_LINE_LIMIT = 14
        private const val LANG_KOTLIN = 0
        private const val LANG_JAVA = 1
        private const val LANG_C = 2
        private const val LANG_PYTHON = 3

        private const val enLatinChars = "The quick brown fox jumps over the lazy dog. Is there better Example, isn't? January 0123456789"

        private const val skLatinChars =
            "áíéúäňôÁÉÍÓÔÚÝáäéíóôúýČčĎĹĺĽľŇňŔŕŘřŠšŤťŮůŹźŻżŽž1234567890aijklmqrtyABDEQ"
        /**
         * array of rendering related data for all provided and supported font resources
         * for all new font resources, here the font related data must be added or altered
         */
        private val FONT_PARAMS = arrayOf(
            FontParams(
                fontSize = 10f, fontRes = R.font.adbxtra, fontName = "adbxtra",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 9f, fontRes = R.font.addwb_, fontName = "addwb",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.advanced_pixel_7,
                fontName = "advanced_pixel_7"
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.aerx_font, fontName = "aerx_font",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.aerxtabs_memesbruh03, fontName = "aerxtabs_memesbruh03",
                divider = 1, topOffset = 2, bottomOffset = 1
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.alterebro_pixel_font,
                fontName = "alterebro_pixel_font"
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.american_cursive, fontName = "american_cursive",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.andina,
                fontName = "andina"
            ), //27
            FontParams(
                fontSize = 16f,
                fontRes = R.font.angie_atore,
                fontName = "angie_atore"
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.animal_crossing_wild_world,  // 32f
                fontName = "animal_crossing_wild_world",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.babyblue,
                fontName = "babyblue"
            ),
            FontParams(
                fontSize = 16f,  // 1f
                fontRes = R.font.bit_light10_srb,
                fontName = "bit_light10_srb"
            ), // poor
            FontParams(
                fontSize = 8f,
                fontRes = R.font.bitdust1,
                fontName = "bitdust1"
            ),
            FontParams(
                fontSize = 9f,
                fontRes = R.font.bitlow,
                fontName = "bitlow"
            ),
            FontParams(
                fontSize = 41f, fontRes = R.font.bitx_map_font_tfb, fontName = "bitx_map_font_tfb",
                divider = 1, topOffset = 10, bottomOffset = 0
            ),
            FontParams(
                fontSize = 9f,
                fontRes = R.font.blau7pt,
                fontName = "blau7pt"
            ), // wrong font
            FontParams(
                fontSize = 8f,
                fontRes = R.font.bm_mini,
                fontName = "bm_mini"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.bmhaa,
                fontName = "bmhaa"
            ),
            FontParams(
                fontSize = 27f, fontRes = R.font.bncuword, fontName = "bncuword",
                divider = 2, topOffset = 5, bottomOffset = 6
            ),
            FontParams(
                fontSize = 12f,  // 36px
                fontRes = R.font.bodge_r,
                fontName = "bodge_r"
            ),
            FontParams(
                fontSize = 9f,  // 38f
                fontRes = R.font.c_and_c_red_alert_inet,
                fontName = "c_and_c_red_alert_inet"
            ),
            FontParams(
                fontSize = 13f, // 41f
                fontRes = R.font.c_and_c_red_alert_lan,
                fontName = "c_and_c_red_alert_lan"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.charriot_deluxe,
                fontName = "charriot_deluxe"
            ),
            FontParams(
                fontSize = 41f, fontRes = R.font.clacon, fontName = "clacon",
                divider = 1, topOffset = 2, bottomOffset = 1
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.classic_memesbruh03,
                fontName = "classic_memesbruh03"
            ),
            FontParams(
                fontSize = 8f,  // 48px
                fontRes = R.font.code_7x5,
                fontName = "code_7x5"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.cyborg_sister,
                fontName = "cyborg_sister"
            ),
            FontParams(
                fontSize = 8f,  // 56f
                fontRes = R.font.david_sans_condensed,
                fontName = "david_sans_condensed"
            ),
            FontParams(
                fontSize = 10f,   // 40f
                fontRes = R.font.display,
                fontName = "display"
            ),  //10px
            FontParams(
                fontSize = 25f,  // 16f
                fontRes = R.font.emulator,
                fontName = "emulator"
            ), // 1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.endlesstype,
                fontName = "endlesstype"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.enter_command,
                fontName = "enter_command"
            ),
            FontParams(
                fontSize = 10f,  // 41f
                fontRes = R.font.everyday,
                fontName = "everyday"
            ),
            FontParams(
                fontSize = 38f,
                fontRes = R.font.fffac,
                fontName = "fffac"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.fleftex_m,
                fontName = "fleftex_m",
                divider = 1, topOffset = 6, bottomOffset = 0
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.font15x5, fontName = "font15x5",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.font2a03_memesbruh03, fontName = "font2a03_memesbruh03",
                divider = 1, topOffset = 1, bottomOffset = 1
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.font712_serif,
                fontName = "font712_serif"
            ),
            FontParams(
                fontSize = 8f, // 24f
                fontRes = R.font.font7x5, fontName = "font7x5",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),  //16px
            FontParams(
                fontSize = 7f,
                fontRes = R.font.font_8_bit_fortress,
                fontName = "font_8_bit_fortress"
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.free_pixel, fontName = "free_pixel",
                divider = 1, topOffset = 0, bottomOffset = 1
            ),
            FontParams(
                fontSize = 52f,
                fontRes = R.font.fruits,
                fontName = "fruits"
            ),
            FontParams(
                fontSize = 8f,  // 40f
                fontRes = R.font.grand9k_pixel,
                fontName = "grand9k_pixel"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.graph_35_pix,
                fontName = "graph_35_pix"
            ),
            FontParams(
                fontSize = 10f,  //41f
                fontRes = R.font.graphicpixel,
                fontName = "graphicpixel"
            ),
            FontParams(
                fontSize = 12f,
                fontRes = R.font.grudb_lit,
                fontName = "grudb_lit"
            ),  // 1px
            FontParams(
                fontSize = 13f,
                fontRes = R.font.hello_world,
                fontName = "hello_world"
            ),  // 52px
            FontParams(
                fontSize = 16f, fontRes = R.font.heytext, fontName = "heytext",
                divider = 1, topOffset = 2, bottomOffset = 0
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.homespun,
                fontName = "homespun"
            ), // 60px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.igiari,
                fontName = "igiari"
            ),
            FontParams(
                fontSize = 8f,  // 52f
                fontRes = R.font.itty,
                fontName = "itty"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.jd_lcd_rounded,
                fontName = "jd_lcd_rounded"
            ),  // 1px
            FontParams(
                fontSize = 20f, fontRes = R.font.led_calculator, fontName = "led_calculator",
                divider = 2, topOffset = 6, bottomOffset = 2
            ),
            FontParams(
                fontSize = 28f,  // 48f
                fontRes = R.font.lexipa,
                fontName = "lexipa"
            ),
            FontParams(
                fontSize = 41f,
                fontRes = R.font.lilliput_steps,
                fontName = "lilliput_steps"
            ),  // 1px
            FontParams(
                fontSize = 8f, // 52f
                fontRes = R.font.lqdkdz_nospace,
                fontName = "lqdkdz_nospace"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.manual_display,
                fontName = "manual_display"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.mega_man_zx,
                fontName = "mega_man_zx"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.mini_kylie,
                fontName = "mini_kylie",
                divider = 1, topOffset = 1, bottomOffset = 0
            ),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.mini_power,
                fontName = "mini_power"
            ),  // 1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.mini_set2,
                fontName = "mini_set2"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.mix_serif_condense,
                fontName = "mix_serif_condense"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.mmbnthin,
                fontName = "mmbnthin",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 8f,  // 24f
                fontRes = R.font.mojang_regular,
                fontName = "mojang_regular"
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.monaco,
                fontName = "monaco"
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.monobit, fontName = "monobit",
                divider = 1, topOffset = 3, bottomOffset = 1
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.monotype_gerhilt,
                fontName = "monotype_gerhilt"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.moonracr,
                fontName = "moonracr"
            ),  // 1px
            FontParams(
                fontSize = 13f,
                fontRes = R.font.mosarg,
                fontName = "mosarg"
            ),  // 1px
            FontParams(
                fontSize = 10f, fontRes = R.font.mousetrap2, fontName = "mousetrap2",
                divider = 1, topOffset = 3, bottomOffset = 1
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.nano,
                fontName = "nano"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.nds12,
                fontName = "nds12"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.ndsbios_memesbruh03,
                fontName = "ndsbios_memesbruh03"
            ),  //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.notalot25,
                fontName = "notalot25"
            ),  //16px
            FontParams(
                fontSize = 7f, fontRes = R.font.old_school_adventures, fontName = "old_school_adventures",
                divider = 1, topOffset = 2, bottomOffset = 0
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.optixal,
                fontName = "optixal"
            ),
            FontParams(
                fontSize = 41f,
                fontRes = R.font.pf_ronda_seven,
                fontName = "pf_ronda_seven"
            ),  //1px
            FontParams(
                fontSize = 10f, fontRes = R.font.pix_l, fontName = "pix_l",
                divider = 1, topOffset = 0, bottomOffset = 0
            ), //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_block_bb,
                fontName = "pixel_block_bb"
            ),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pixel_operator,
                fontName = "pixel_operator"
            ),  //50px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_operator8,
                fontName = "pixel_operator8"
            ),  //50px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pixel_operator_mono,
                fontName = "pixel_operator_mono"
            ),  //50px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_operator_mono8,
                fontName = "pixel_operator_mono8"
            ),  //50px
            FontParams(
                fontSize = 13f,
                fontRes = R.font.pixelade,
                fontName = "pixelade"
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pixur,
                fontName = "pixur"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.plastica,
                fontName = "plastica"
            ),  //1px
            FontParams(
                fontSize = 10f, fontRes = R.font.poco, fontName = "poco",
                divider = 1, topOffset = 3, bottomOffset = 1
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.post_pixel_7,
                fontName = "post_pixel_7"
            ),  //1px
            FontParams(
                fontSize = 20f,
                fontRes = R.font.primusscript,
                fontName = "primusscript"
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.proggy_tiny,
                fontName = "proggy_tiny"
            ),  //1px
            FontParams(
                fontSize = 14f,
                fontRes = R.font.px10,
                fontName = "px10"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.px_sans_nouveaux,
                fontName = "px_sans_nouveaux"
            ),
            FontParams(fontSize = 10f, fontRes = R.font.pxl, fontName = "pxl"),  //1px
            FontParams(
                fontSize = 8f, fontRes = R.font.r_classic_8, fontName = "r_classic_8",
                divider = 1, topOffset = 1, bottomOffset = 0
            ),
            FontParams(
                fontSize = 13f,
                fontRes = R.font.redensek,
                fontName = "redensek"
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.resource,
                fontName = "resource"
            ), //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.rev_mini_pixel,
                fontName = "rev_mini_pixel"
            ),  //1px
            FontParams(
                fontSize = 41f,
                fontRes = R.font.rififi_serif,
                fontName = "rififi_serif"
            ),
            FontParams(fontSize = 8f, fontRes = R.font.rix, fontName = "rix"),
            FontParams(
                fontSize = 7f, fontRes = R.font.rntg_larger, fontName = "rntg_larger",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),  //1px
            FontParams(
                fontSize = 20f, fontRes = R.font.root_square, fontName = "root_square",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),  //1px
            FontParams(
                fontSize = 8f,  //40px
                fontRes = R.font.rosesare_ff0000,
                fontName = "rosesare_ff0000"
            ), //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.rse_handwriting_pixel,
                fontName = "rse_handwriting_pixel"
            ), //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.rtt_redstar_8,
                fontName = "rtt_redstar_8"
            ),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.savior1,
                fontName = "savior1"
            ),  //1px
            FontParams(
                fontSize = 8f, fontRes = R.font.scifibit, fontName = "scifibit",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.sevastopol_interface,
                fontName = "sevastopol_interface"
            ),  //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.sg04,
                fontName = "sg04"
            ),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.sgk075,
                fontName = "sgk075"
            ),  //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.silly_pixel,
                fontName = "silly_pixel"
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.smt_devil_survivor,
                fontName = "smt_devil_survivor"
            ),  // 1px
            FontParams(
                fontSize = 7f,
                fontRes = R.font.somybmp01_7,
                fontName = "somybmp01_7"
            ),  // 1px
// FontParams(fontSize = 16f, fontRes = R.font.sprite_comic, fontName = "sprite_comic"),
            FontParams(
                fontSize = 40f,
                fontRes = R.font.st01r,
                fontName = "st01r",
                divider = 4, topOffset = 0, bottomOffset = 0
            ),  // 1px
            FontParams(
                fontSize = 10f,
                fontRes = R.font.supernat1001,
                fontName = "supernat1001"
            ),  // 1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.tama_mini02,
                fontName = "tama_mini02"
            ),  // 1px
            FontParams(
                fontSize = 25f, fontRes = R.font.techkr, fontName = "techkr",
                divider = 1, topOffset = 12, bottomOffset = 0
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.teeny_pix,
                fontName = "teeny_pix"
            ),  // 1px
            FontParams(
                fontSize = 20f,
                fontRes = R.font.thin_pixel_7,
                fontName = "thin_pixel_7"
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.threebyfive_memesbruh03,
                fontName = "threebyfive_memesbruh03"
            ),  // 1px
            FontParams(
                fontSize = 6f,
                fontRes = R.font.tiny,
                fontName = "tiny"
            ),  //1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.tiny_unicode,
                fontName = "tiny_unicode"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.tloz_minish_cap_a_link_to_the_past_four_sword,
                fontName = "tloz_minish_cap_a_link_to_the_past_four_sword"
            ),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.tloz_phantom_hourglass,
                fontName = "tloz_phantom_hourglass"
            ),  //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.uni05_53,
                fontName = "uni05_53"
            ),
            FontParams(
                fontSize = 9f,
                fontRes = R.font.volter28goldfish_29,
                fontName = "volter28goldfish_29"
            ),  //1px
            FontParams(
                fontSize = 8f,
                fontRes = R.font.zepto_regular,
                fontName = "zepto_regular"
            ), //1px
            //  *******************************************
            //   NEW   FONTS  1
            //  *******************************************
            FontParams(
                fontSize = 20f,
                fontRes = R.font.a_futuricalt_thin,
                fontName = "a_futuricalt_thin"
            ),  // 1px
            FontParams(
                fontSize = 40f,
                fontRes = R.font.accius_t_ot_light_condensed,
                fontName = "accius_t_ot_light_condensed"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.aclonica,
                fontName = "aclonica"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.advanced_sans_serif_7,
                fontName = "advanced_sans_serif_7"
            ),    // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.air_conditioner,
                fontName = "air_conditioner"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.amita_regular,
                fontName = "amita_regular"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.anyway_light,
                fontName = "anyway_light"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.apple_chancery,
                fontName = "apple_chancery"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.arimo_regular,
                fontName = "arimo_regular"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.artcraft_urw_treg,
                fontName = "artcraft_urw_treg"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.augie,
                fontName = "augie"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.baksheesh_thin,
                fontName = "baksheesh_thin"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.boring_boring,
                fontName = "boring_boring"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.calligraffitti_regular,
                fontName = "calligraffitti_regular"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.century_gothic,
                fontName = "century_gothic"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.century_regular,
                fontName = "century_regular"
            ),  // 1px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.century_schoolbook_normal,
                fontName = "century_schoolbook_normal"
            ),  // 41px
            FontParams(
                fontSize = 30f,
                fontRes = R.font.centuryschool,
                fontName = "centuryschool"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.chalet_comprime_cologne_sixty,
                fontName = "chalet_comprime_cologne_sixty"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.chalet_london_nineteen_seventy,
                fontName = "chalet_london_nineteen_seventy"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.chalet_newyork_nineteen_seventy,
                fontName = "chalet_newyork_nineteen_seventy"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.chalk_dust,
                fontName = "chalk_dust"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.chancery,
                fontName = "chancery"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.chancery_regular,
                fontName = "chancery_regular"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.cherry_cream_soda,
                fontName = "cherry_cream_soda"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.chevin_medium,
                fontName = "chevin_medium"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.chewy,
                fontName = "chewy"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.classico_urw_t_ot,
                fontName = "classico_urw_t_ot"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.coming_soon,
                fontName = "coming_soon"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.commerce_black_condensed_ssi_bold_condensed,
                fontName = "commerce_black_condensed_ssi_bold_condensed"
            ),
            FontParams(
                fontSize = 32f,
                fontRes = R.font.concetta,
                fontName = "concetta"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.conformity_normal,
                fontName = "conformity_normal"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.context_light_condensed_ssi_light_condensed,
                fontName = "context_light_condensed_ssi_light_condensed"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.context_reprise_condensed_ssi_condensed,
                fontName = "context_reprise_condensed_ssi_condensed"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.context_reprise_thin_ssi_thin,
                fontName = "context_reprise_thin_ssi_thin"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.context_semi_condensed_ssi_semi_condensed,
                fontName = "context_semi_condensed_ssi_semi_condensed"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.courier_std_medium,
                fontName = "courier_std_medium"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.cousine_regular,
                fontName = "cousine_regular"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.crafty_girls,
                fontName = "crafty_girls"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.creepster_regular,
                fontName = "creepster_regular"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.critical,
                fontName = "critical"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.crocs,
                fontName = "crocs"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.crushed,
                fontName = "crushed"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.d3_cubism,
                fontName = "d3_cubism"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.damned_architect,
                fontName = "damned_architect"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.darling_nikki,
                fontName = "darling_nikki"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.dateline_condensed_plain,
                fontName = "dateline_condensed_plain"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.daves_hand_regular,
                fontName = "daves_hand_regular"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.david_sans_condensed,
                fontName = "david_sans_condensed"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.dawn_castle,
                fontName = "dawn_castle"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.daxline_pro_thin,
                fontName = "daxline_pro_thin"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.defatted_milk_condensed,
                fontName = "defatted_milk_condensed"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.dejavu_sans,
                fontName = "dejavu_sans"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.dejavu_sans_condensed,
                fontName = "dejavu_sans_condensed"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.dejavu_sans_mono,
                fontName = "dejavu_sans_mono"
            ),
            FontParams(
                fontSize = 36f,
                fontRes = R.font.dejavu_serif_condensed,
                fontName = "dejavu_serif_condensed"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.dekorator_sto,
                fontName = "dekorator_sto"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.delitsch_antiqua,
                fontName = "delitsch_antiqua"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.desert_crypt,
                fontName = "desert_crypt"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.desert_dog_hmk,
                fontName = "desert_dog_hmk"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.diablo_heavy,
                fontName = "diablo_heavy",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.dialog_regular,
                fontName = "dialog_regular"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.diamond_gothic,
                fontName = "diamond_gothic"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.diegolo,
                fontName = "diegolo"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.digital_serial_regular_db,
                fontName = "digital_serial_regular_db"
            ),
            FontParams(fontSize = 20f, fontRes = R.font.din, fontName = "din"),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.din_light,
                fontName = "din_light"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.din_light_alternate,
                fontName = "din_light_alternate"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.din_pro_light,
                fontName = "din_pro_light"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.din_pro_regular,
                fontName = "din_pro_regular"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.disney_park,
                fontName = "disney_park"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.dn_manuscript,
                fontName = "dn_manuscript"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.dolores_cyrillic_regular,
                fontName = "dolores_cyrillic_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.don_casio,
                fontName = "don_casio"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.donna_bodoni_aa_script_pdf,
                fontName = "donna_bodoni_aa_script_pdf"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.donna_bodoni_ce_script_pdf,
                fontName = "donna_bodoni_ce_script_pdf"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.doodle_kid,
                fontName = "doodle_kid"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.dot_matrix_normal,
                fontName = "dot_matrix_normal"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.dot_matrix_regular,
                fontName = "dot_matrix_regular"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.draft_gothic_thin,
                fontName = "draft_gothic_thin"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.draft_plate_condensed,
                fontName = "draft_plate_condensed"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.draftsman_normal,
                fontName = "draftsman_normal"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.dtl_prokyon_st_light,
                fontName = "dtl_prokyon_st_light"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.duc_de_berry,
                fontName = "duc_de_berry"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.dummy_boy,
                fontName = "dummy_boy"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.dzeragir_decorative_italic,
                fontName = "dzeragir_decorative_italic"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.elementric,
                fontName = "elementric"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.erin_go_bragh_condensed,
                fontName = "erin_go_bragh_condensed"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.everlicious,
                fontName = "everlicious"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.ft19_condensed_regular,
                fontName = "ft19_condensed_regular"
            ),
            FontParams(
                fontSize = 30f,
                fontRes = R.font.ft24_extracondensed_medium,
                fontName = "ft24_extracondensed_medium"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.fujiyama2,
                fontName = "fujiyama2"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.gapstown,
                fontName = "gapstown"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.garamond_light_condensed_ssi_light_condensed,
                fontName = "garamond_light_condensed_ssi_light_condensed"
            ),
            FontParams(
                fontSize = 28f,
                fontRes = R.font.gracie,
                fontName = "gracie"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.graphium_thin,
                fontName = "graphium_thin"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.greenbeans_thin,
                fontName = "greenbeans_thin"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.greymagus,
                fontName = "greymagus"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.grutchgrotesk_condensed_light,
                fontName = "grutchgrotesk_condensed_light"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.harry,
                fontName = "harry"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.harvey,
                fontName = "harvey"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.high_sign,
                fontName = "high_sign"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.hipchick,
                fontName = "hipchick"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.ibmplex_sans_condensed_light,
                fontName = "ibmplex_sans_condensed_light"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.ibmplex_sans_condensed_regular,
                fontName = "ibmplex_sans_condensed_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.ibmplex_sans_condensed_thin,
                fontName = "ibmplex_sans_condensed_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.inconsolata_regular,
                fontName = "inconsolata_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.interstate_light_condensed,
                fontName = "interstate_light_condensed"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.jagged_dreams,
                fontName = "jagged_dreams"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.just_another_hand,
                fontName = "just_another_hand"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.kenzou,
                fontName = "kenzou"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.kepler_std_light_condensed_display,
                fontName = "kepler_std_light_condensed_display"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.kepler_std_light_semicondensed_caption,
                fontName = "kepler_std_light_semicondensed_caption"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.kepler_std_light_semicondensed_display,
                fontName = "kepler_std_light_semicondensed_display"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.kepler_std_semicondensed,
                fontName = "kepler_std_semicondensed"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.kktdmag,
                fontName = "kktdmag"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.lato_light,
                fontName = "lato_light"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.lato_regular,
                fontName = "lato_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.lavi,
                fontName = "lavi",
                divider = 1, topOffset = 1, bottomOffset = 0
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.lekton04_thin,
                fontName = "lekton04_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.liberation_mono_regular,
                fontName = "liberation_mono_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.maiden_orange,
                fontName = "maiden_orange"
            ),
            FontParams(fontSize = 24f, fontRes = R.font.mcg, fontName = "mcg"),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.montez_regular,
                fontName = "montez_regular"
            ),
            FontParams(fontSize = 24f, fontRes = R.font.nee, fontName = "nee"),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.nk57_monospace_ex_lt,
                fontName = "nk57_monospace_ex_lt"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.nk57_monospace_ex_rg,
                fontName = "nk57_monospace_ex_rg"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.nk57_monospace_sc_lt,
                fontName = "nk57_monospace_sc_lt"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.nk57_monospace_se_rg,
                fontName = "nk57_monospace_se_rg"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.open_sans_condlight,
                fontName = "open_sans_condlight"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.open_sans_light,
                fontName = "open_sans_light"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.open_sans_regular,
                fontName = "open_sans_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.orbitron_bold_webfont,
                fontName = "orbitron_bold_webfont"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.orbitron_light,
                fontName = "orbitron_light"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.orbitron_light_webfont,
                fontName = "orbitron_light_webfont"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.orbitron_medium,
                fontName = "orbitron_medium"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.permanent_marker,
                fontName = "permanent_marker"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.pill_gothic_300mg_thin,
                fontName = "pill_gothic_300mg_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.quilline_script_thin,
                fontName = "quilline_script_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.rabiohead,
                fontName = "rabiohead"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.rancho_regular,
                fontName = "rancho_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.redressed,
                fontName = "redressed"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.ribeye_regular,
                fontName = "ribeye_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.roboto_condensed_light,
                fontName = "roboto_condensed_light"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.roboto_condensed_regular,
                fontName = "roboto_condensed_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.roboto_light,
                fontName = "roboto_light"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.roboto_regular,
                fontName = "roboto_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.rochester_regular,
                fontName = "rochester_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.rocketscript,
                fontName = "rocketscript"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.sans_thin_thin,
                fontName = "sans_thin_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.satisfy_regular,
                fontName = "satisfy_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.schoolbell,
                fontName = "schoolbell"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.schwaben_alt_thin_thin,
                fontName = "schwaben_alt_thin_thin"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.skinny,
                fontName = "skinny"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.slackey,
                fontName = "slackey"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.smokum_regular,
                fontName = "smokum_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.source_sans_pro_regular,
                fontName = "source_sans_pro_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.source_sans_pro_light,
                fontName = "source_sans_pro_light"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.speciale_lite,
                fontName = "speciale_lite"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.stint_ultra_condensed_regular,
                fontName = "stint_ultra_condensed_regular"
            ),
            FontParams(fontSize = 24f, fontRes = R.font.sun, fontName = "sun"),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.sunshiney,
                fontName = "sunshiney"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.syncopate_regular,
                fontName = "syncopate_regular"
            ),
            FontParams(
                fontSize = 42f,
                fontRes = R.font.thaiga_thin,
                fontName = "thaiga_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.tinos_regular,
                fontName = "tinos_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.ultra,
                fontName = "ultra"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.unisolv3c,
                fontName = "unisolv3c"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.unkempt_regular,
                fontName = "unkempt_regular"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.urw_grotesk_t_ot_light,
                fontName = "urw_grotesk_t_ot_light"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.urw_grotesk_t_ot_light_condensed,
                fontName = "urw_grotesk_t_ot_light_condensed"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.vag_rounded_light_ssi_thin,
                fontName = "vag_rounded_light_ssi_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.vag_rounded_std_thin,
                fontName = "vag_rounded_std_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.vag_rounded_thin,
                fontName = "vag_rounded_thin"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.walter_turncoat,
                fontName = "walter_turncoat"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.whatever,
                fontName = "whatever"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.wind,
                fontName = "wind"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.yacarena_ultra_personal_use,
                fontName = "yacarena_ultra_personal_use"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.font_5mikropix,
                fontName = "5mikropix"
            ),
            FontParams(
                fontSize = 23f,
                fontRes = R.font.font_8bit_operator_plus_8_regular,
                fontName = "8bit_operator_plus_8_regular"
            ),
            FontParams(
                fontSize = 23f,
                fontRes = R.font.font_8bit_operator_plus_regular,
                fontName = "8bit_operator_plus_regular"
            ),
            FontParams(
                fontSize = 24f, // 1f
                fontRes = R.font.advo_cut,
                fontName = "advo_cut"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.altima,
                fontName = "altima"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.asai_analogue,
                fontName = "asai_analogue"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.atarismall,
                fontName = "atari_small"
            ),
            FontParams(
                fontSize = 24f, // 1f
                fontRes = R.font.aux_dot_bitc,
                fontName = "aux_dot_bitc"
            ),
            FontParams(
                fontSize = 24f, // 1f
                fontRes = R.font.avant_pixel,
                fontName = "avant_pixel"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.bf_mnemonika_regular,
                fontName = "bf_mnemonika_regular"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.bit_potion_medium,
                fontName = "bit_potion_medium"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.bitpotionext,
                fontName = "bit_potionext"
            ),
            FontParams(
                fontSize = 24f, // 1f
                fontRes = R.font.bm_plain,
                fontName = "bm_plain"
            ),
            FontParams(
                fontSize = 24f, // 1f
                fontRes = R.font.brandi_alexander,
                fontName = "brandi_alexander"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.brandi_alexander_outline,
                fontName = "brandi_alexander_outline"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.ceriph_07_55,
                fontName = "ceriph_07_55"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.chainreact_block_boxter,
                fontName = "chainreact_block_boxter"
            ),
            FontParams(
                fontSize = 20f, // 1f
                fontRes = R.font.cherry_cream_soda_regular,
                fontName = "cherry_cream_soda_regular"
            ),
            FontParams(
                fontSize = 14f,
                fontRes = R.font.chunky_dunk,
                fontName = "chunky_dunk"
            ),
            FontParams(
                fontSize = 14f,
                fontRes = R.font.chunky_dunk_outlin,
                fontName = "chunky_dunk_outlin"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.comicoro,
                fontName = "comicoro"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.crazy_creation,
                fontName = "crazy_creation"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.crunchy_beef_outline,
                fontName = "crunchy_beef_outline"
            ),
            FontParams(
                fontSize = 12f,
                fontRes = R.font.cube_bit,
                fontName = "cube_bit"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.dina_10px,
                fontName = "dina_10px"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.draconian_pixels_minimal,
                fontName = "draconian_pixels_minimal"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.emoticomic,
                fontName = "emoticomic"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.exe_pixel_perfect,
                fontName = "exe_pixel_perfect"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.f77_minecraft,
                fontName = "f77_minecraft"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.flower_pixellowzz,
                fontName = "flower_pixellowzz"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.golden_sun,
                fontName = "golden_sun"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.hgjb,
                fontName = "hgjb"
            ),
            FontParams(
                fontSize = 11f,
                fontRes = R.font.hi_ai,
                fontName = "hi_ai"
            ),
            FontParams(
                fontSize = 11f,
                fontRes = R.font.hi_aic,
                fontName = "hi_aic"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.kharon,
                fontName = "kharon"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.kubasta,
                fontName = "kubasta"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.lcd_dot_tr,
                fontName = "lcd_dot_tr"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.legendaria,
                fontName = "legendaria"
            ),
            FontParams(
                fontSize = 14f,
                fontRes = R.font.minimum,
                fontName = "minimum"
            ),
            FontParams(
                fontSize = 31f,
                fontRes = R.font.minimum_plus1,
                fontName = "minimum_plus1"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.mosaico_regular,
                fontName = "mosaico_regular"
            ),
            FontParams(
                fontSize = 12f,
                fontRes = R.font.onesize_reverse,
                fontName = "onesize_reverse"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.orochii_fon,
                fontName = "orochii_fon"
            ),
            FontParams(
                fontSize = 9f,
                fontRes = R.font.peepo,
                fontName = "peepo"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pf_tempesta_seven,
                fontName = "pf_tempesta_seven"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pf_tempesta_seven_compressed,
                fontName = "pf_tempesta_seven_compressed"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pf_tempesta_seven_condensed,
                fontName = "pf_tempesta_seven_condensed"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pix_ideal,
                fontName = "pix_ideal"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_berry_08_84_ltd_edition,
                fontName = "pixel_berry_08_84_ltd_edition",
                divider = 1, topOffset = 1, bottomOffset = 0
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_josh6,
                fontName = "pixel_josh6"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pixel_letters_full,
                fontName = "pixel_letters_full"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_mix_micro,
                fontName = "pixel_mix_micro"
            ),
            FontParams(
                fontSize = 23f,
                fontRes = R.font.pixel_six00,
                fontName = "pixel_six00"
            ),
            FontParams(
                fontSize = 24f, // 1f
                fontRes = R.font.pixel_six10,
                fontName = "pixel_six10"
            ),
            FontParams(
                fontSize = 24f,  // 1px
                fontRes = R.font.pixel_six14,
                fontName = "pixel_six14"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_sleigh,
                fontName = "pixel_sleigh"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pixel_unicode,
                fontName = "pixel_unicode",
                divider = 1, topOffset = 2, bottomOffset = 2
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.pixel_western,
                fontName = "pixel_western"
            ),
            FontParams(
                fontSize = 13f,
                fontRes = R.font.pixelate_bass,
                fontName = "pixelate_bass"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.pixelates,
                fontName = "pixelates"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pixerif,
                fontName = "pixerif"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pixolde,
                fontName = "pixolde"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pkmn_mystery_dungeon,
                fontName = "pkmn_mystery_dungeon"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.pocket_memesbruh03,
                fontName = "pocket_memesbruh03"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.portable_tube,
                fontName = "portable_tube"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.proggy_clean,
                fontName = "proggy_clean"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.proggy_small,
                fontName = "proggy_small"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.puke_force_8,
                fontName = "puke_force_8"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.rasterman,
                fontName = "rasterman"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.rcr,
                fontName = "rcr"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.reactor7,
                fontName = "reactor7"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.root_beer,
                fontName = "root_beer"
            ),
            FontParams(
                fontSize = 13f,
                fontRes = R.font.screeny_normal,
                fontName = "screeny_normal"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.serious_1,
                fontName = "serious_1"
            ),
            FontParams(
                fontSize = 20f,
                fontRes = R.font.sf_pixelate,
                fontName = "sf_pixelate"
            ),
            FontParams(
                fontSize = 9f,
                fontRes = R.font.small_talk,
                fontName = "small_talk"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.smithesyzer,
                fontName = "smithesyzer"
            ),
            FontParams(
                fontSize = 24f,
                fontRes = R.font.stadium,
                fontName = "stadium"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.standard_09_56,
                fontName = "standard_09_56"
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.superhelio_extended,
                fontName = "superhelio_extended",
                divider = 1, topOffset = 2, bottomOffset = 0
            ),
            FontParams(
                fontSize = 10f,
                fontRes = R.font.supernat_1002,
                fontName = "supernat_1002"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.sweet_dreams,
                fontName = "sweet_dreams"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.typecast,
                fontName = "typecast"
            ),
            FontParams(
                fontSize = 8f,
                fontRes = R.font.v5_myopia,
                fontName = "v5_myopia.ttf"
            )
        )

        private const val MESSAGE =
            "Žiadny príklad by nebol krajší, než takýto krásny text s dĺžňami a mäkčeňmi rýdzo po slovensky."

        /**
         * show message for selected font - compacted to fit the display screen
         *
         * @param message  demo text
         */
        fun renderGraphicsMessageCompacted3(
            fontPreview: FontPreview,
            message: String = MESSAGE,
            rowOffset: Int = 0,
            useLarge: Boolean = true
        ) {
            var charIndex = -1
            var rowIndex = rowOffset
            var lastRowEndCharIndex = -1
            val messageLength = message.length
            val width = fontPreview.getScreenWidth(useLarge)
            val height = fontPreview.getScreenHeight(useLarge)

            fontPreview.clearBitmap(useLarge)
            val widths = ArrayList<Int>(20)
            while (charIndex < messageLength - 1 && rowIndex <= height) {
                var pixelCount = 0

                widths.clear()
                while (charIndex < messageLength - 1 && pixelCount < width) {
                    val nextCharIndex = charIndex + 1
                    val charValue = message[nextCharIndex]
                    val charPixelsWidth = fontPreview.getFontDataWidth(charValue)
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
                    charsDataList.add(fontPreview.getFontData(charArray[i]))
                }

                val charsData = charsDataList.toTypedArray()
                if (charsData.isNotEmpty()) {
                    val rowCharWidths = widths.toTypedArray()
                    val charDataHeight = charsData[0].size
//                logMsg("row[" + rowIndex + ", " + startIndex + "] " + rowSize + " chars Height " + charDataHeight + "px")

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
                                val charDataWidth = rowCharWidths[rowCharIndex]
                                if (i < charData.size) {
                                    val fontChar = charData[i]

                                    for (k in 0 until charDataWidth) {
                                        rowPixelFlags[pos + k] = 1 shl charDataWidth - k - 1 and fontChar != 0
                                    }
                                }
                                pos += charDataWidth
                            }
                        }
                        fontPreview.setRowPixelArray(0, actRowIndex, rowPixelFlags, useLarge)
                    }
                    rowIndex += charDataHeight
                }
            }
        }

        private fun logMsg(msg: String) = android.util.Log.d("FontRender", msg)
    }
}