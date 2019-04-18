package com.rudolas.mia.fontrenderer

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.support.annotation.LayoutRes
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import java.io.File
import kotlin.math.max
import kotlin.text.StringBuilder
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.AfterPermissionGranted

/**
 * class to preview available true type fonts with extended latin characters such as diacritics.
 * For preselected font there is rendered detailed preview of several characters for font text sizes from 5px to 64 px.
 * User can then choose which text size matches the native font resolution the best.
 * For such native text size complete ascii characters map is rendered into byte pixels for java/kotlin code use. E.g. for raspbery pi, android things java/kotlin apps.
 * C code to be supported on request
 *
 * Actual supported ASCII characters:  2 ascii ranges merged into array of char pixel bytes: one for kotlin and one for java
 *     ASCII 32-126    space .. ~
 *     ASCII 161..382  ¡ .. À .. ž
 *     optionally additional greek and coptic, armenian, cyrillic, hebrew or arabic ASCII subsets could be enabled
 *
 * listed:
 * " !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
 * "¡¢£¤¥¦§¨©ª«¬­®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿĀāĂăĄą"
 * "ĆćĈĉĊċČčĎďĐđĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħĨĩĪīĬĭĮįİıĲĳĴĵĶķĸĹĺĻļĽľĿŀŁłŃńŅņŇňŉŊŋŌōŎŏŐőŒœŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŧŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽž"
 *
 * result code arrays are stored into SDcard directory /sdcard/Download/Fonts/    e.g. filename FONT_TINY_UNICODE_16PX.txt
 *
 * for each character is generated pixel bytes row by row upon native true type font text size,
 *    e.g 6x12 pixels matrix, corresponding to width and height of rendered char with native font size 16 pixels
 *
 * output kotlin code sample :
 *
 *
 * private val FONT_TINY_UNICODE_16PX = arrayOf( // FONT 16px tiny_unicode.ttf
 *     intArrayOf(0x00, 0x00, 0x00, 0x1C, 0x22, 0x1C, 0x22, 0x22, 0x1C, 0x00, 0x00, 0x00), // 6x12 '8' 0x38
 *     intArrayOf(0x00, 0x00, 0x00, 0x08, 0x08, 0x08, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00), // 6x12 '!' 0x21
 *     ...
 * )
 * private val FONT_TINY_UNICODE_16PX_WIDTH = intArrayOf(      //  rendered ascii char pixels width, mono fonts have fixed size chars
 *     5,2,4,6,5,4,6,2,3,3,           // ' '..')' 0x20
 *     4,4,3,4,2,4,5,3,5,5,           // '*'..'3' 0x2A
 *     5,5,5,5,5,5,2,3,3,5,           // '4'..'=' 0x34
 *     ...
 * )
 *
 */
class FontsCharPixelsActivity : AppCompatActivity(), View.OnClickListener {
    override fun onClick(view: View) {
        logMsg("onClick")
        if (isDetailIntentAction()) {
            fontIndex++
            previewOrGeneratePixelCodeArrays(false)
        } else {
            startActivity(
                Intent(this, FontsCharPixelsActivity::class.java)
                    .setAction(ACTION_DETAIL)
                    .putExtra("fontIndex", view.tag as Int)
            )
        }
    }

    private var onGlobalLayoutListenerBitmapsCheck: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var fontIndex = 0
    //    private var fontIndexToRender = 0
    private lateinit var fontCharTextView: TextView
    private lateinit var fontTitleTextView: TextView
    private lateinit var fontLatinCharsView: TextView
    private var bitmapsLayouts: Array<LinearLayout>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            fontIndex = savedInstanceState.getInt("fontIndex")
        }

        if (isDetailIntentAction()) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun isDetailIntentAction() = intent.action == ACTION_DETAIL

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        fontIndex = savedInstanceState.getInt("fontIndex")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("fontIndex", fontIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        fontIndex = intent.getIntExtra("fontIndex", 0)
        methodRequiresStorageWritePermission()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.render, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> onBackPressed().let { true }
            R.id.menuRender -> startRenderFont().let { true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    @AfterPermissionGranted(PERMISSION_WRITE_STORAGE)
    private fun methodRequiresStorageWritePermission() {
        val perms = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (EasyPermissions.hasPermissions(this, *perms)) {
            // Already have permission, do the thing
            previewOrGeneratePixelCodeArrays(!isDetailIntentAction())
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, getString(R.string.storage_write_rationale),
                PERMISSION_WRITE_STORAGE, *perms
            )
        }
    }

    private fun startRenderFont() {
        fontTexts.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListenerBitmapsCheck)
        onGlobalLayoutListenerBitmapsCheck = null
        val latinCharactersString = getExtendedLatinCharactersString()
        fontLatinCharsView.text = latinCharactersString
        setCharTextSize(FONT_PARAMS[fontIndex].fontSize)
        fontCharTextView.text = latinCharactersString[0].toString()
        generateFontCharactersCodeArray(toRender = true)
    }

    /**
     * either preview whole ascii chars for all available fonts - font index auto increment
     * or generate for the hardcoded font index the kotlin and java char pixels arrays
     */
    private fun previewOrGeneratePixelCodeArrays(
        toPreview: Boolean
    ) {
        if (toPreview) {
            previewFontCharactersCodeArray()
        } else {
//            fontIndex = 4
            generateFontCharactersCodeArray(
                true
//                , toGenerateCharPixelsPreview = true
            )
        }
    }

    /**
     * preview latin ascii chars for all available fonts from /res/font directory
     */
    private fun previewFontCharactersCodeArray() {
//        val skChars = "ľščťžýáíéúäňôČŇĽĎŠŽŤ~${161.toChar()}${190.toChar()}${191.toChar()}${192.toChar()}"
        //        val fontChars = "abcdefghijklmnoprqstuvwxyz ABCDEFGHIJKLMNOPRQSTUVWXYZ \n1234567890_+=:.,;/!?<>{}[]()"

        val latinCharacters = getExtendedLatinCharactersString()
        for (index in FONT_PARAMS.indices) {
//        for (index in fonts.iterator()) {
            updateFontPreview(index, latinCharacters, forceCreate = true)
        }
        /*fontTexts.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    for (index in 0 until fontTexts.childCount step 4) {
                        val textView = fontTexts.getChildAt(2) as TextView
                        val measuredWidth = textView.measuredWidth
                        val measuredHeight = textView.height
                        val isEmpty = measuredWidth == 0 || measuredHeight == 0
                        val charsBitmap = Bitmap.createBitmap(
                            if (isEmpty) 1 else measuredWidth,
                            if (isEmpty) 1 else measuredHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        textView.draw(Canvas(charsBitmap))
                        logMsg("SK: [$index] ${fontNames[index]} Bitmap ${charsBitmap.width}x${charsBitmap.height}")
                        // Todo: save bitmap containing all provided ascii latin chars
                        charsBitmap.recycle()
                    }
                    fontTexts.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )*/
    }

    /**
     * generate font character bitmaps as kotlin AND java integer arrays. Output is into logCat.
     * Optionally also bitmap pixels preview for each font char array item is generated into output.
     * e.g.
     *    KOTLIN array :
     * private val FONT_x8 = arrayOf( // FONT_16px aerxtabs_memesbruh03.ttf
     *        char '!' pixels preview - optional
     *     // 000000 [0]
     *     // 000000 [1]
     *     // 000000 [2]
     *     // 001000 [3]
     *     // 001000 [4]
     *     // 001000 [5]
     *     // 001000 [6]
     *     // 000000 [7]
     *     // 001000 [8]
     *     // 000000 [9]
     *     // 000000 [10]
     *     // 000000 [11]
     *     intArrayOf(0x00, 0x00, 0x00, 0x08, 0x08, 0x08, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00), // 6x12 '!' 0x21
     *        char '8' pixels preview - optional
     *     // 000000 [0]
     *     // 000000 [1]
     *     // 000000 [2]
     *     // 011100 [3]
     *     // 100010 [4]
     *     // 011100 [5]
     *     // 100010 [6]
     *     // 100010 [7]
     *     // 011100 [8]
     *     // 000000 [9]
     *     // 000000 [10]
     *     // 000000 [11]
     *     intArrayOf(0x00, 0x00, 0x00, 0x1C, 0x22, 0x1C, 0x22, 0x22, 0x1C, 0x00, 0x00, 0x00), // 6x12 '8' 0x38
     *     ...
     * )
     *   JAVA array :
     * private static final int[][] FONT_x8 = { // FONT_16px aerxtabs_memesbruh03.ttf
     *     // ... char pixels preview - optional
     *     {0x00, 0x00, 0x00, 0x08, 0x08, 0x08, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00}, // 6x12 '!' 0x21
     *     // ... char pixels preview - optional
     *     {0x00, 0x00, 0x00, 0x1C, 0x22, 0x1C, 0x22, 0x22, 0x1C, 0x00, 0x00, 0x00}, // 6x12 '8' 0x38
     *     ...
     * }
     */
    private fun generateFontCharactersCodeArray(
//        fontIndex: Int,
//        fontParams: FontParams,
        toCheckBitmaps: Boolean = false,
        toRender: Boolean = false,
        toGenerateCharPixelsPreview: Boolean = false
    ) {
//        val textPixelSize = fontParams.fontSize

//        val latinCharacters = "!8"
        val latinCharacters = getExtendedLatinCharactersString()
//        logMsg("SK: ${latinCharacters.length}")
//        val index = fonts[fontIndex]
        val toCreate = fontTexts.childCount == 0
        updateFontPreview(fontIndex, latinCharacters, toCheckBitmaps, toCreate)

        if (toCreate || !toRender && onGlobalLayoutListenerBitmapsCheck == null || toRender) {
            addGlobalLayoutListener(latinCharacters, toCheckBitmaps, toGenerateCharPixelsPreview)
        }
    }

    private fun addGlobalLayoutListener(
        latinCharacters: String,
        toCheckBitmaps: Boolean,
        toGenerateCharPixelsPreview: Boolean
    ) {
        fontTexts.viewTreeObserver.addOnGlobalLayoutListener(
            if (toCheckBitmaps) {
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    private var charIndex = 0

                    override fun onGlobalLayout() {
                        if (charIndex >= charsToShowAsBitmaps.length) {
                            charIndex = 0
                            return
                        }
                        val measuredWidth = fontCharTextView.measuredWidth
                        val measuredHeight = fontCharTextView.height
                        //                        logMsg("SK: [$index] ${fontNames[index]} [$charIndex] ${fontCharTextView.text}")
                        val isEmpty = measuredWidth == 0 || measuredHeight == 0
                        val charsBitmap = Bitmap.createBitmap(
                            if (isEmpty) 1 else measuredWidth,
                            if (isEmpty) 1 else measuredHeight,
                            Bitmap.Config.ARGB_8888
                        )

                        val textSize = fontCharTextView.textSize.toInt()
                        val diff = textSize - 5
//                        logMsg("SK: [$fontIndex] Bitmap ${charsBitmap.width}x${charsBitmap.height} ${FONT_PARAMS[fontIndex].fontName} ${textSize}px [$charIndex]='${fontCharTextView.text}' Bitmap[$diff] $charIndex size ${fontCharTextView.textSize.toInt()}")
                        fontCharTextView.draw(Canvas(charsBitmap))

//                        if (diff == 0 && charIndex == 0 && fontIndex == 0) {
                        if (bitmapsLayouts == null) {
                            bitmapsLayouts = Array(charsToShowAsBitmaps.length * BITMAPS_LINES) {
                                (layoutInflater.inflate(R.layout.font_bitmaps, fontTexts, false) as LinearLayout)
                                    .apply { fontTexts.addView(this) }
                            }
                        }
                        //                        logMsg("SK: [$index] ${fontNames[index]} [$charIndex] Bitmap[$diff] ${fontCharTextView.text}")
                        val toSwap = textSize == 64
                        setCharTextSize(if (toSwap) 5f else textSize.toFloat() + 1)
                        val scaleFactor = SCALE * (if (diff < BITMAPS_COUNT) 2 else 1)
                        (bitmapsLayouts?.get(BITMAPS_LINES * charIndex + (diff / BITMAPS_COUNT))?.getChildAt((diff % BITMAPS_COUNT) * 2) as? ImageView)
//                            .apply { logMsg("SK: bitmapsLayouts [${3 * charIndex + (diff / BITMAPS_COUNT)}] ${diff / BITMAPS_COUNT} child ${diff % BITMAPS_COUNT}") }
                            ?.setImageBitmap(
                                Bitmap.createScaledBitmap(
                                    charsBitmap,
                                    charsBitmap.width * scaleFactor,
                                    charsBitmap.height * scaleFactor,
                                    false
                                )
                            )
                        charsBitmap.recycle()
                        if (!toSwap) {
                            //                        index = fonts[++fontIndex]
                        } else if (++charIndex < charsToShowAsBitmaps.length) {
                            fontCharTextView.text = charsToShowAsBitmaps[charIndex].toString()
                        } else {
                            fontCharTextView.text = charsToShowAsBitmaps[0].toString()
                            //                            fontTexts.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                    }
                }.apply { onGlobalLayoutListenerBitmapsCheck = this }
            } else {
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    private val stringPreviewBuilder = StringBuilder(40)
                    private val stringHexBuilder = StringBuilder(40)
                    private val stringFileBuilder = StringBuilder(2000)
                    private val widthsArray = IntArray(ASCII_LATIN_COUNT)
                    private var charIndex = 0
                    private var toKotlin = true
                    private lateinit var pixels: Array<Array<Boolean>>
                    private var pixelWidthMax: Int = 0
                    private var pixelHeightMax: Int = 0
                    private lateinit var fontFile: File
                    
                    override fun onGlobalLayout() {

                        val fontParams = FONT_PARAMS[fontIndex]
                        val fontName = fontParams.fontName
                        val measuredWidth = fontCharTextView.measuredWidth
                        val measuredHeight = fontCharTextView.measuredHeight
                        //                logMsg("SK: [$index] $fontName [$charIndex] ${fontCharTextView.text}")
                        val isEmpty = measuredWidth == 0 || measuredHeight == 0
                        val charBitmap = Bitmap.createBitmap(
                            if (isEmpty) 1 else measuredWidth,
                            if (isEmpty) 1 else measuredHeight,
                            Bitmap.Config.ARGB_8888
                        )

                        if (charIndex == 0) {
                            val fontSize = fontParams.fontSize.toInt()
                            val arrayName = "FONT_${fontName.toUpperCase()}_${fontSize}PX"
                            appendFontFile(
                                "private ${
                                if (toKotlin) "val $arrayName = arrayOf(" else "static final int[][] $arrayName = {"
                                } // FONT ${fontCharTextView.textSize.toInt()}px $fontName.ttf"
                            )
                            pixels = Array(fontSize * 3) { Array(fontSize * 3) { false } }
                            val downloadDir = File(Environment.getExternalStorageDirectory(), "Download")
                            val fontsDir = File(downloadDir, "Fonts")
                            if (!fontsDir.exists() && !fontsDir.mkdir()) {
                                logMsg("CANNOT ACCESS KeywordsDownload directory")
                                return
                            }
                            fontFile = File(fontsDir, "$arrayName.txt")
                            if (!fontFile.exists()) {
                                logMsg("Font file to be created ${fontFile.absolutePath}")
                            }
                        }


                        val divider = fontParams.divider
                        fontCharTextView.draw(Canvas(charBitmap))
                        // ignore char 64 line feed
                        val bitmapWidth = if (charIndex == 64) 1 else charBitmap.width / divider
                        val bitmapHeight = (charBitmap.height - fontParams.bottomOffset) / divider
                        widthsArray[charIndex] = bitmapWidth

                        val hasNoTopOffset = fontParams.topOffset == 0
                        if (hasNoTopOffset && charIndex != 64) {
                            pixelHeightMax = max(bitmapHeight, pixelHeightMax)
                            pixelWidthMax = max(bitmapWidth, pixelWidthMax)
//                            logMsg("SK: Max bitmap $pixelWidthMax x $pixelHeightMax '${fontCharTextView.text}'")
                        }
                        val topOffset = fontParams.topOffset
                        for (y in topOffset until bitmapHeight * divider step divider) {
                            var byte = 0
                            for (x in 0 until bitmapWidth * divider step divider) {
                                val isPixelOn = charBitmap.getPixel(x, y) != 0
                                if (hasNoTopOffset && isPixelOn && !pixels[y][x]) {
                                    pixels[y][x] = true
                                }
                                val pixel = if (isPixelOn) 1 else 0
                                byte += pixel shl (bitmapWidth - x / divider - 1)
                                if (toGenerateCharPixelsPreview) {
                                    stringPreviewBuilder.append(pixel)
                                }
                            }
                            if (toGenerateCharPixelsPreview) {
                                appendFontFile("// $stringPreviewBuilder [$y]") // binary char pixels preview
                                stringPreviewBuilder.clear()
                            }
                            appendHexString(stringHexBuilder.append(if (y == topOffset) "" else ", ").apply {
                                val indexY = (y - topOffset) / divider
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
                        val isNotLastChar = charIndex < ASCII_LATIN_COUNT - 1
                        if (!isNotLastChar) {
                            stringHexBuilder.append(' ')
                        }
                        val spacer = stringHexBuilder.toString()

                        stringHexBuilder.clear()
                        val charText = fontCharTextView.text
                        appendHexString(stringHexBuilder, charText[0].toInt())
                        val hexAscii = stringHexBuilder.toString()
                        stringHexBuilder.clear()

                        appendFontFile(
                            if (isEmpty) {
                                "    ${if (toKotlin) "IntArray(0)" else "{}"}, // blank font char '$charText' $hexAscii"
                            } else {
                                "    ${if (toKotlin) "intArrayOf($rowBytes)" else "{$rowBytes}"}${
                                if (isNotLastChar) "," else ""
                                }$spacer // [$charIndex] ${bitmapWidth}x${bitmapHeight - topOffset / divider} '$charText' $hexAscii"
                            }
                        )
                        //fontTexts.getChildAt(3).background = BitmapDrawable(resources, charBitmap)
                        charBitmap.recycle() // recycle manually if not assigned to imageView

                        when {
                            ++charIndex < latinCharacters.length -> assignLatinCharToRender()
                            toKotlin -> {
                                writeArrayEnd()
                                toKotlin = false
                                charIndex = 0
                                assignLatinCharToRender()
                            }
                            else -> {
                                fontTexts.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                writeArrayEnd()
                                if (hasNoTopOffset) {
                                    appendFontFile("// Max Bitmap $pixelWidthMax x $pixelHeightMax")
                                    for (y in 0 until pixelHeightMax) {
                                        for (x in 0 until pixelWidthMax) {
                                            stringPreviewBuilder.append(if (pixels[y][x]) '#' else '.')
                                        }
                                        appendFontFile("// Mass Matrix $stringPreviewBuilder $y")
                                        stringPreviewBuilder.clear()
                                    }
                                }
                                fontFile.sink(false).buffer().use {
                                    it.write(stringFileBuilder.toString().encodeUtf8())
                                }
                                stringFileBuilder.clear()

                                fontLatinCharsView.text = skChars
                                charIndex = 0
                                setCharTextSize(5f)
                            }
                        }
                    }

                    private fun appendFontFile(line: String) {
                        stringFileBuilder.append(line).append("\n")
//                        logMsg("SK: $line")
                    }

                    private fun appendHexString(stringBuilder: StringBuilder, ascii: Int) {
                        val hexString = Integer.toHexString(ascii).toUpperCase()
                        stringBuilder.append("0x")
                            .append(if (hexString.length == 1) "0" else "")
                            .append(hexString)
                    }

                    private fun assignLatinCharToRender() {
                        fontCharTextView.text = latinCharacters[charIndex].toString()
                    }

                    private fun writeArrayEnd() {
                        appendFontFile(if (toKotlin) ")" else "}")
                        val fontParams = FONT_PARAMS[fontIndex]
                        val fontCharWidth =
                            "FONT_${fontParams.fontName.toUpperCase()}_${fontParams.fontSize.toInt()}PX_WIDTH"
                        appendFontFile(
                            "private ${
                            if (toKotlin) "val $fontCharWidth" else "static final int[] $fontCharWidth"
                            } = ${
                            if (toKotlin) "intArrayOf(" else "{"
                            }"
                        )

                        for (i in widthsArray.indices) {
                            stringPreviewBuilder.append(widthsArray[i])
                                .append(if (i < ASCII_LATIN_COUNT - 1) "," else "")
                            if (i % 10 == 9) {
                                for (j in 0..29 - stringPreviewBuilder.length) {
                                    stringPreviewBuilder.append(' ')
                                }
                                val firstChar = latinCharacters[i - 9]
                                appendHexString(stringHexBuilder, firstChar.toInt())
                                appendFontFile("    $stringPreviewBuilder // '$firstChar'..'${latinCharacters[i]}' $stringHexBuilder")
                                stringPreviewBuilder.clear()
                                stringHexBuilder.clear()
                            }
                        }
                        if (stringPreviewBuilder.isNotEmpty()) {
                            for (j in 0..29 - stringPreviewBuilder.length) {
                                stringPreviewBuilder.append(' ')
                            }
                            appendFontFile("    $stringPreviewBuilder //    ..'${latinCharacters[widthsArray.indices.last]}'")
                            stringPreviewBuilder.clear()
                        }
                        appendFontFile(if (toKotlin) ")" else "}")
                    }
                }
            })
    }

    private fun setCharTextSize(textSize: Float) {
        fontCharTextView.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            textSize
        )
    }

    private fun updateFontPreview(
        index: Int,
        latinCharacters: String,
        toCheckBitmaps: Boolean = false,
        forceCreate: Boolean = false
    ) {
        val fontParams = FONT_PARAMS[index]
//        logMsg("SK: [$index] ${fontParams.fontName} ${resources.getResourceName(fontParams.fontRes)}")
        if (forceCreate) {
            fontTitleTextView = inflateTextView()
            with(fontTitleTextView) {
                setOnClickListener(this@FontsCharPixelsActivity)
                tag = index
                setBackgroundColor(Color.GREEN)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 56f)
                fontTexts.addView(this)
            }

            val frame = layoutInflater.inflate(R.layout.text_view_framed, fontTexts, false) as ViewGroup
            fontTexts.addView(frame)
            fontCharTextView = frame.getChildAt(0) as TextView
            with(fontCharTextView) {
                //                text = fontChars
                text = (if (toCheckBitmaps) charsToShowAsBitmaps[0] else latinCharacters[0]).toString()
                setTextSize(TypedValue.COMPLEX_UNIT_PX, if (toCheckBitmaps) 5f else fontParams.fontSize)
            }

            fontLatinCharsView = inflateTextView()
            with(fontLatinCharsView) {
                setOnClickListener(this@FontsCharPixelsActivity)
                tag = index
                text = if (toCheckBitmaps) skChars else latinCharacters
                fontTexts.addView(this)
            }
            // spacer
            with(inflateTextView(R.layout.text_view_centered)) {
                //            background = BitmapDrawable(resources, textAsBitmap("ľščťžýáíéúäňôČŇĽĎŠŽŤ", 36f, Color.BLACK))
                fontTexts.addView(this)
            }
        }

        fontTitleTextView.text = "$index. ${fontParams.fontName} ${fontParams.fontSize.toInt()}px"
        val font = resources.getFont(fontParams.fontRes)
        fontCharTextView.typeface = font
        fontLatinCharsView.typeface = font
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
    private fun getExtendedLatinCharactersString(): String {
        val strBuilder = StringBuilder(ASCII_LATIN_COUNT)
        for (int in ASCII_LATIN_RANGE1 + ASCII_LATIN_RANGE2) {
            strBuilder.append(int.toChar())
        }
        return strBuilder.toString()
    }

    private fun inflateTextView(@LayoutRes layoutResId: Int = R.layout.text_view) =
        layoutInflater.inflate(layoutResId, fontTexts, false) as TextView

    private data class FontParams(
        internal var divider: Int = 1,
        internal var topOffset: Int = 0,
        internal var bottomOffset: Int = 0,
        internal var fontSize: Float,
        internal var fontRes: Int,
        internal var fontName: String
    )

    companion object {

        // 43 graph , 52 led calculator, 4 aerxtabs   28 fonts
        private val fonts = arrayOf(
            4, 6, 10, 18, 23, 26, 31, 36, 37, 40, 42, 43, 49, 52, 61, 65, 69, 71, 75, 79, 80, 81, 82, 86 /*poco*/,
            88, 91, 92, 123
        )

        private val FONT_PARAMS = arrayOf(
            FontParams(fontSize = 20f, fontRes = R.font.adbxtra, fontName = "adbxtra"),
            FontParams(fontSize = 18f, fontRes = R.font.addwb_, fontName = "addwb_"),
            FontParams(fontSize = 20f, fontRes = R.font.advanced_pixel_7, fontName = "advanced_pixel_7"),
            FontParams(fontSize = 32f, fontRes = R.font.aerx_font, fontName = "aerx_font"),
            FontParams(
                fontSize = 16f, fontRes = R.font.aerxtabs_memesbruh03, fontName = "aerxtabs_memesbruh03",
                divider = 1, topOffset = 2, bottomOffset = 1
            ),
            FontParams(fontSize = 16f, fontRes = R.font.alterebro_pixel_font, fontName = "alterebro_pixel_font"),
            FontParams(
                fontSize = 16f, fontRes = R.font.american_cursive, fontName = "american_cursive",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(fontSize = 16f, fontRes = R.font.andina, fontName = "andina"), //27
            FontParams(fontSize = 16f, fontRes = R.font.angie_atore, fontName = "angie_atore"),
            FontParams(
                fontSize = 32f, fontRes = R.font.animal_crossing_wild_world,
                fontName = "animal_crossing_wild_world"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.babyblue, fontName = "babyblue"),
            FontParams(fontSize = 16f, fontRes = R.font.bit_light10_srb, fontName = "bit_light10_srb"), // poor
            FontParams(fontSize = 28f, fontRes = R.font.bitdust1, fontName = "bitdust1"),
            FontParams(fontSize = 28f, fontRes = R.font.bitlow, fontName = "bitlow"),
            FontParams(
                fontSize = 41f, fontRes = R.font.bitx_map_font_tfb, fontName = "bitx_map_font_tfb",
                divider = 1, topOffset = 10, bottomOffset = 0
            ),
            FontParams(fontSize = 9f, fontRes = R.font.blau7pt, fontName = "blau7pt"),
            FontParams(fontSize = 20f, fontRes = R.font.bm_mini, fontName = "bm_mini"),
            FontParams(fontSize = 30f, fontRes = R.font.bmhaa, fontName = "bmhaa"),
            FontParams(fontSize = 27f, fontRes = R.font.bncuword, fontName = "bncuword"),
            FontParams(fontSize = 36f, fontRes = R.font.bodge_r, fontName = "bodge_r"),
            FontParams(fontSize = 38f, fontRes = R.font.c_and_c_red_alert_inet, fontName = "c_and_c_red_alert_inet"),
            FontParams(fontSize = 41f, fontRes = R.font.c_and_c_red_alert_lan, fontName = "c_and_c_red_alert_lan"),
            FontParams(fontSize = 10f, fontRes = R.font.charriot_deluxe, fontName = "charriot_deluxe"),
            FontParams(
                fontSize = 41f, fontRes = R.font.clacon, fontName = "clacon",
                divider = 1, topOffset = 2, bottomOffset = 1
            ),
            FontParams(fontSize = 16f, fontRes = R.font.classic_memesbruh03, fontName = "classic_memesbruh03"),
            FontParams(fontSize = 48f, fontRes = R.font.code_7x5, fontName = "code_7x5"),
            FontParams(fontSize = 16f, fontRes = R.font.cyborg_sister, fontName = "cyborg_sister"),
            FontParams(fontSize = 56f, fontRes = R.font.david_sans_condensed, fontName = "david_sans_condensed"),
            FontParams(fontSize = 40f, fontRes = R.font.display, fontName = "display"),  //10px
// FontParams(fontSize = 16f, fontRes = R.font.emoticomic, fontName = "emoticomic"),
            FontParams(fontSize = 1f, fontRes = R.font.emulator, fontName = "emulator"),
            FontParams(fontSize = 8f, fontRes = R.font.endlesstype, fontName = "endlesstype"),
            FontParams(fontSize = 16f, fontRes = R.font.enter_command, fontName = "enter_command"),
            FontParams(fontSize = 41f, fontRes = R.font.everyday, fontName = "everyday"),
            FontParams(fontSize = 38f, fontRes = R.font.fffac, fontName = "fffac"),
            FontParams(fontSize = 8f, fontRes = R.font.fleftex_m, fontName = "fleftex_m"),
            FontParams(fontSize = 32f, fontRes = R.font.font15x5, fontName = "font15x5"),
            FontParams(
                fontSize = 16f, fontRes = R.font.font2a03_memesbruh03, fontName = "font2a03_memesbruh03",
                divider = 1, topOffset = 1, bottomOffset = 1
            ),
            FontParams(fontSize = 16f, fontRes = R.font.font712_serif, fontName = "font712_serif"),
            FontParams(fontSize = 24f, fontRes = R.font.font7x5, fontName = "font7x5"),  //16px
            FontParams(fontSize = 7f, fontRes = R.font.font_8_bit_fortress, fontName = "font_8_bit_fortress"),
            FontParams(
                fontSize = 16f, fontRes = R.font.free_pixel, fontName = "free_pixel",
                divider = 1, topOffset = 0, bottomOffset = 1
            ),
            FontParams(fontSize = 52f, fontRes = R.font.fruits, fontName = "fruits"),
            FontParams(fontSize = 40f, fontRes = R.font.grand9k_pixel, fontName = "grand9k_pixel"),
            FontParams(fontSize = 8f, fontRes = R.font.graph_35_pix, fontName = "graph_35_pix"),
            FontParams(fontSize = 41f, fontRes = R.font.graphicpixel, fontName = "graphicpixel"),
            FontParams(fontSize = 1f, fontRes = R.font.grudb_lit, fontName = "grudb_lit"),
            FontParams(fontSize = 13f, fontRes = R.font.hello_world, fontName = "hello_world"),  // 52px
            FontParams(
                fontSize = 16f, fontRes = R.font.heytext, fontName = "heytext",
                divider = 1, topOffset = 2, bottomOffset = 0
            ),
            FontParams(fontSize = 20f, fontRes = R.font.homespun, fontName = "homespun"), // 60px
            FontParams(fontSize = 16f, fontRes = R.font.igiari, fontName = "igiari"),
            FontParams(fontSize = 52f, fontRes = R.font.itty, fontName = "itty"),
            FontParams(fontSize = 1f, fontRes = R.font.jd_lcd_rounded, fontName = "jd_lcd_rounded"),
            FontParams(
                fontSize = 20f, fontRes = R.font.led_calculator, fontName = "led_calculator",
                divider = 1, topOffset = 6, bottomOffset = 2
            ),
            FontParams(fontSize = 48f, fontRes = R.font.lexipa, fontName = "lexipa"),
            FontParams(fontSize = 16f, fontRes = R.font.lilliput_steps, fontName = "lilliput_steps"),  // 1px
            FontParams(fontSize = 52f, fontRes = R.font.lqdkdz_nospace, fontName = "lqdkdz_nospace"),
            FontParams(fontSize = 36f, fontRes = R.font.manual_display, fontName = "manual_display"),
            FontParams(fontSize = 40f, fontRes = R.font.mega_man_zx, fontName = "mega_man_zx"),
            FontParams(fontSize = 1f, fontRes = R.font.mini_kylie, fontName = "mini_kylie"),
            FontParams(fontSize = 1f, fontRes = R.font.mini_power, fontName = "mini_power"),
            FontParams(fontSize = 8f, fontRes = R.font.mini_set2, fontName = "mini_set2"),
            FontParams(fontSize = 16f, fontRes = R.font.mix_serif_condense, fontName = "mix_serif_condense"),
            FontParams(fontSize = 32f, fontRes = R.font.mmbnthin, fontName = "mmbnthin"),
            FontParams(fontSize = 24f, fontRes = R.font.mojang_regular, fontName = "mojang_regular"),  //1px
            FontParams(fontSize = 32f, fontRes = R.font.monaco, fontName = "monaco"),
            FontParams(
                fontSize = 16f, fontRes = R.font.monobit, fontName = "monobit",
                divider = 1, topOffset = 3, bottomOffset = 1
            ),
            FontParams(fontSize = 8f, fontRes = R.font.monotype_gerhilt, fontName = "monotype_gerhilt"),
            FontParams(fontSize = 1f, fontRes = R.font.moonracr, fontName = "moonracr"),
            FontParams(fontSize = 16f, fontRes = R.font.mosarg, fontName = "mosarg"),  // 1px
            FontParams(
                fontSize = 10f, fontRes = R.font.mousetrap2, fontName = "mousetrap2",
                divider = 1, topOffset = 3, bottomOffset = 1
            ),
            FontParams(fontSize = 10f, fontRes = R.font.nano, fontName = "nano"),
            FontParams(fontSize = 10f, fontRes = R.font.nds12, fontName = "nds12"),
            FontParams(fontSize = 16f, fontRes = R.font.ndsbios_memesbruh03, fontName = "ndsbios_memesbruh03"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.notalot25, fontName = "notalot25"),  //16px
            FontParams(
                fontSize = 7f, fontRes = R.font.old_school_adventures, fontName = "old_school_adventures",
                divider = 1, topOffset = 2, bottomOffset = 0
            ),
            FontParams(fontSize = 16f, fontRes = R.font.optixal, fontName = "optixal"),
// FontParams(fontSize = 16f, fontRes = R.font.peepo, fontName = "peepo"),
            FontParams(fontSize = 16f, fontRes = R.font.pf_ronda_seven, fontName = "pf_ronda_seven"),  //1px
            FontParams(fontSize = 20f, fontRes = R.font.pix_l, fontName = "pix_l"), //1px
            FontParams(fontSize = 1f, fontRes = R.font.pixel_block_bb, fontName = "pixel_block_bb"),
            FontParams(fontSize = 16f, fontRes = R.font.pixel_operator, fontName = "pixel_operator"),  //50px
            FontParams(fontSize = 8f, fontRes = R.font.pixel_operator8, fontName = "pixel_operator8"),  //50px
            FontParams(fontSize = 16f, fontRes = R.font.pixel_operator_mono, fontName = "pixel_operator_mono"),  //50px
            FontParams(fontSize = 8f, fontRes = R.font.pixel_operator_mono8, fontName = "pixel_operator_mono8"),  //50px
            FontParams(fontSize = 13f, fontRes = R.font.pixelade, fontName = "pixelade"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.pixur, fontName = "pixur"),
            FontParams(fontSize = 16f, fontRes = R.font.plastica, fontName = "plastica"),  //1px
            FontParams(
                fontSize = 10f, fontRes = R.font.poco, fontName = "poco",
                divider = 1, topOffset = 3, bottomOffset = 1
            ),
            FontParams(fontSize = 40f, fontRes = R.font.post_pixel_7, fontName = "post_pixel_7"),  //1px
            FontParams(fontSize = 20f, fontRes = R.font.primusscript, fontName = "primusscript"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.proggy_tiny, fontName = "proggy_tiny"),  //1px
            FontParams(fontSize = 14f, fontRes = R.font.px10, fontName = "px10"),
            FontParams(fontSize = 10f, fontRes = R.font.px_sans_nouveaux, fontName = "px_sans_nouveaux"),
            FontParams(fontSize = 10f, fontRes = R.font.pxl, fontName = "pxl"),  //1px
            FontParams(
                fontSize = 8f, fontRes = R.font.r_classic_8, fontName = "r_classic_8",
                divider = 1, topOffset = 1, bottomOffset = 0
            ),
            FontParams(fontSize = 13f, fontRes = R.font.redensek, fontName = "redensek"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.resource, fontName = "resource"), //1px
            FontParams(fontSize = 8f, fontRes = R.font.rev_mini_pixel, fontName = "rev_mini_pixel"),  //1px
            FontParams(fontSize = 41f, fontRes = R.font.rififi_serif, fontName = "rififi_serif"),
            FontParams(fontSize = 8f, fontRes = R.font.rix, fontName = "rix"),
            FontParams(fontSize = 21f, fontRes = R.font.rntg_larger, fontName = "rntg_larger"),  //1px
            FontParams(fontSize = 20f, fontRes = R.font.root_square, fontName = "root_square"),  //1px
            FontParams(fontSize = 40f, fontRes = R.font.rosesare_ff0000, fontName = "rosesare_ff0000"), //1px
            FontParams(
                fontSize = 20f,
                fontRes = R.font.rse_handwriting_pixel,
                fontName = "rse_handwriting_pixel"
            ), //1px
            FontParams(fontSize = 1f, fontRes = R.font.rtt_redstar_8, fontName = "rtt_redstar_8"),
            FontParams(fontSize = 16f, fontRes = R.font.savior1, fontName = "savior1"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.scifibit, fontName = "scifibit"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.sevastopol_interface, fontName = "sevastopol_interface"),  //1px
            FontParams(fontSize = 1f, fontRes = R.font.sg04, fontName = "sg04"),
            FontParams(fontSize = 16f, fontRes = R.font.sgk075, fontName = "sgk075"),  //1px
            FontParams(fontSize = 8f, fontRes = R.font.silly_pixel, fontName = "silly_pixel"),  //1px
            FontParams(fontSize = 1f, fontRes = R.font.smt_devil_survivor, fontName = "smt_devil_survivor"),
            FontParams(fontSize = 1f, fontRes = R.font.somybmp01_7, fontName = "somybmp01_7"),
// FontParams(fontSize = 16f, fontRes = R.font.sprite_comic, fontName = "sprite_comic"),
            FontParams(fontSize = 1f, fontRes = R.font.st01r, fontName = "st01r"),
            FontParams(fontSize = 1f, fontRes = R.font.supernat1001, fontName = "supernat1001"),
            FontParams(fontSize = 1f, fontRes = R.font.tama_mini02, fontName = "tama_mini02"),
            FontParams(
                fontSize = 25f, fontRes = R.font.techkr, fontName = "techkr",
                divider = 1, topOffset = 12, bottomOffset = 0
            ),
            FontParams(fontSize = 1f, fontRes = R.font.teeny_pix, fontName = "teeny_pix"),
            FontParams(fontSize = 20f, fontRes = R.font.thin_pixel_7, fontName = "thin_pixel_7"),  //1px
            FontParams(fontSize = 1f, fontRes = R.font.threebyfive_memesbruh03, fontName = "threebyfive_memesbruh03"),
            FontParams(fontSize = 30f, fontRes = R.font.tiny, fontName = "tiny"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.tiny_unicode, fontName = "tiny_unicode"),
            FontParams(
                fontSize = 1f,
                fontRes = R.font.tloz_minish_cap_a_link_to_the_past_four_sword,
                fontName = "tloz_minish_cap_a_link_to_the_past_four_sword"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.tloz_phantom_hourglass,
                fontName = "tloz_phantom_hourglass"
            ),  //1px
            FontParams(fontSize = 8f, fontRes = R.font.uni05_53, fontName = "uni05_53"),
            FontParams(fontSize = 9f, fontRes = R.font.volter28goldfish_29, fontName = "volter28goldfish_29"),  //1px
            FontParams(fontSize = 8f, fontRes = R.font.zepto_regular, fontName = "zepto_regular") //1px
        )

        private const val PERMISSION_WRITE_STORAGE = 4
        //        private const val CHECK_BITMAPS = true
        private const val CHECK_BITMAPS = false
        //        private const val divider = 1
//        private const val topOffset = 2//6
//        private const val bottomOffset = 1//2
        private const val SCALE = 4
        private const val BITMAPS_COUNT = 15
        private const val BITMAPS_LINES = 4
        private const val HEX_VALUES_LINE_LIMIT = 14
        private const val FONT_SIZE = 16f

        private const val ACTION_DETAIL = "ACTION_DETAIL"

        private val ASCII_LATIN_RANGE1 = 32..126    // space .. ~
        private val ASCII_LATIN_RANGE2 = 161..382 // ¡ .. À .. ž
        private val ASCII_LATIN_COUNT =
            ASCII_LATIN_RANGE1.last - ASCII_LATIN_RANGE1.first + ASCII_LATIN_RANGE2.last - ASCII_LATIN_RANGE2.first + 2
        //        val charsToShowAsBitmaps = "8"
//        private const val charsToShowAsBitmaps = "8Ź"
        private const val charsToShowAsBitmaps = "ýô8Ź0#"
        private const val skChars = "8Ź!01#\$/@QÁŽČčĎŢţŤťáäýóô"
//        private const val charsToShowAsBitmaps = "8Ź!01#\$/@QÁŽČčĎŢţŤťáäýóôaefghijklmnpqrwxy"

        private fun logMsg(msg: String) = android.util.Log.d("FontRenderer", msg)
    }
}
