package com.rudolas.mia.fontrenderer

import android.Manifest
import android.content.Context
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
import android.support.v7.app.AlertDialog
import android.util.TypedValue
import android.view.*
import android.widget.ArrayAdapter
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
import kotlin.math.min

/**
 * class to preview available true type fonts with extended latin characters such as diacritics.
 * For preselected font there is rendered detailed preview of several characters for font text sizes from 5px to 64 px.
 * User can then choose which text size matches the native font resolution the best.
 * For such native text size complete ascii characters map is rendered into byte pixels for java/kotlin code use. E.g. for raspberry pi, android things java/kotlin apps.
 * C code to be supported on request.
 *
 * Activity main features:
 * - preview available true type fonts with extended latin characters such as diacritics.
 * - preview selected ASCII chars in font sizes from 5px to 64 px to choose the best match for the native font size resolution
 * - render complete ascii characters map into byte pixels array for java/kotlin code use.
 *
 * Actual supported ASCII characters:  2 ascii ranges merged into array of char pixel bytes: one for kotlin and one for java
 * - ASCII 32-126    space .. ~
 * - ASCII 161..382  ¡ .. À .. ž
 * - optionally additional greek and coptic, armenian, cyrillic, hebrew or arabic ASCII subsets could be enabled
 *
 * listed:
 *
 * " !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~
 * ¡¢£¤¥¦§¨©ª«¬­®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿĀāĂăĄą
 * ĆćĈĉĊċČčĎďĐđĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħĨĩĪīĬĭĮįİıĲĳĴĵĶķĸĹĺĻļĽľĿŀŁłŃńŅņŇňŉŊŋŌōŎŏŐőŒœŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŧŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽž"
 *
 * Result code arrays are stored into SDcard directory /sdcard/Download/Fonts/    e.g. filename TinyUnicode16px.kt
 *
 * for each character is generated pixel bytes row by row upon native true type font text size,
 *
 *    e.g 6x12 pixels matrix, corresponding to width and height of rendered char with native font size 16 pixels
 * Hint: To avoid compilation troubles with generated font bytes arrays, e.g. java code too large,
 * keep the render font size as low as possible to match native font size or to render non excessive bitmaps.
 *
 * Output kotlin code sample :
 *
 * <code>
 *
 *      class TinyUnicode16px {
 *          companion object {
 *              val font = FontItem( // FONT 16px tiny_unicode.ttf
 *                  "TINY_UNICODE_16PX",
 *                  charBytes =  = arrayOf( // FONT 16px tiny_unicode.ttf
 *                      intArrayOf(0x00, 0x00, 0x00, 0x1C, 0x22, 0x1C, 0x22, 0x22, 0x1C, 0x00, 0x00, 0x00), // 6x12 '8' 0x38
 *                      intArrayOf(0x00, 0x00, 0x00, 0x08, 0x08, 0x08, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00), // 6x12 '!' 0x21
 *                      ...
 *                  ),
 *                  widths = intArrayOf(      //  rendered ascii char pixels width, mono fonts have fixed size chars
 *                      5,2,4,6,5,4,6,2,3,3,           // ' '..')' 0x20
 *                      4,4,3,4,2,4,5,3,5,5,           // '*'..'3' 0x2A
 *                      5,5,5,5,5,5,2,3,3,5,           // '4'..'=' 0x34
 *                      ...
 *                  )
 *              )
 *         }
 *     }
 *     // Max Bitmap 14 x 13 Offsets [Top=0, Bottom=0]
 *     // Mass Matrix - merged text preview from all characters rendered into one map
 *     // Mass Matrix ############## 0
 *     // Mass Matrix ############## 1
 *     // Mass Matrix ############## 2
 *     // Mass Matrix ############## 3
 *     // Mass Matrix ############## 4
 *     // Mass Matrix ############## 5
 *     // Mass Matrix ############## 6
 *     // Mass Matrix ############## 7
 *     // Mass Matrix ############## 8
 *     // Mass Matrix ############## 9
 *     // Mass Matrix ############## 10
 *     // Mass Matrix ############.. 11
 *     // Mass Matrix ##########.... 12
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.render, menu)
        if (!isDetailIntentAction()) {
            menu.findItem(R.id.menuRender).isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * back press and Render action bar items handling
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> onBackPressed().let { true }
            R.id.menuGoTo -> showGoToFontDialog().let { true }
            R.id.menuRender -> startRenderFont().let { true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * easy permissions dynamic request handling
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    /**
     * easy permissions dynamic request result processing
     */
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

    /**
     * Entry point to launch rendering of all supported ascii chars into target language files
     */
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
//        fontTexts.viewTreeObserver.addOnGlobalLayoutListener(
//            object : ViewTreeObserver.OnGlobalLayoutListener {
//                override fun onGlobalLayout() {
//                    fontTexts.viewTreeObserver.removeOnGlobalLayoutListener(this)
//                }
//            })

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
     *
     * KOTLIN array :
     * <code>
     *
     *      class TinyUnicode16px {
     *          companion object {
     *              val font = FontItem( // FONT 16px tiny_unicode.ttf
     *              ...
     *              // char '!' pixels preview - optional
     *              // 000000 [0]
     *              // 000000 [1]
     *              // 000000 [2]
     *              // 001000 [3]
     *              // 001000 [4]
     *              // 001000 [5]
     *              // 001000 [6]
     *              // 000000 [7]
     *              // 001000 [8]
     *              // 000000 [9]
     *              // 000000 [10]
     *              // 000000 [11]
     *              intArrayOf(0x00, 0x00, 0x00, 0x08, 0x08, 0x08, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00), // 6x12 '!' 0x21
     *              // char '8' pixels preview - optional
     *              // 000000 [0]
     *              // 000000 [1]
     *              // 000000 [2]
     *              // 011100 [3]
     *              // 100010 [4]
     *              // 011100 [5]
     *              // 100010 [6]
     *              // 100010 [7]
     *              // 011100 [8]
     *              // 000000 [9]
     *              // 000000 [10]
     *              // 000000 [11]
     *              intArrayOf(0x00, 0x00, 0x00, 0x1C, 0x22, 0x1C, 0x22, 0x22, 0x1C, 0x00, 0x00, 0x00), // 6x12 '8' 0x38
     *              ...
     *      )
     * </code>
     *
     * JAVA array :
     * <code>
     *
     *      package fonts;
     *      public class TinyUnicode16px {
     *          private static final int[][] charsPixels = {
     *              // ... char pixels preview - optional
     *              {0x00, 0x00, 0x00, 0x08, 0x08, 0x08, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00}, // 6x12 '!' 0x21
     *              // ... char pixels preview - optional
     *              {0x00, 0x00, 0x00, 0x1C, 0x22, 0x1C, 0x22, 0x22, 0x1C, 0x00, 0x00, 0x00}, // 6x12 '8' 0x38
     *              ...
     *          };
     *
     *          public static final FontItem FONT = new FontItem(
     *              "TINY_UNICODE_16PX",
     *              charsPixels,
     *              widths
     *          );
     *      }
     * </code>
     */
    private fun generateFontCharactersCodeArray(
        toCheckBitmaps: Boolean = false,
        toRender: Boolean = false,
        toGenerateCharPixelsPreview: Boolean = false
    ) {
//        val latinCharacters = "!8"
        val latinCharacters = getExtendedLatinCharactersString()
//        logMsg("SK: ${latinCharacters.length}")
        val toCreate = fontTexts.childCount == 0
        updateFontPreview(fontIndex, latinCharacters, toCheckBitmaps, toCreate)

        if (toCreate || !toRender && onGlobalLayoutListenerBitmapsCheck == null || toRender) {
            addGlobalLayoutListener(latinCharacters, toCheckBitmaps, toGenerateCharPixelsPreview)
        }
    }

    /**
     * global layout listener used to await char layout formatting according to requested font type and font size.
     * It loops through either all font sizes from 5px until 64px or supported ascii font chars
     * Also whole font rendering here is repeated for all target languages supported.
     *
     * @param latinCharacters string as an array of latin ascii chars to render
     * @param toCheckBitmaps true to preview 5px..64px font sizes into rendered char bitmaps, otherwise false to avoid bitmaps rendering
     * @param toGenerateCharPixelsPreview true to generate text form character pixels map , where preview '#' as pixel on and '.' as an empty pixel
     *
     */
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
                    private val stringFileBuilders = Array(2) { StringBuilder(2000) }
                    private val widthsArray = IntArray(ASCII_LATIN_COUNT)
                    private var charIndex = 0
                    private lateinit var pixels: Array<Array<Boolean>>
                    private var pixelWidthMax: Int = 0
                    private var pixelHeightMax: Int = 0
                    private lateinit var fontFiles: Array<File>

                    override fun onGlobalLayout() {

                        val fontParams = FONT_PARAMS[fontIndex]
                        val fontName = fontParams.fontName
                        if (charIndex < 1 || charIndex > 315) {
                            logMsg("SK: Render START [$fontIndex] $fontName ${fontParams.fontSize.toInt()}px [$charIndex] '${latinCharacters[charIndex]}'")
                        }
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
                            val nameParts = fontName.split('_')
                            val arrayName = "${fontName.toUpperCase()}_${fontSize}px"
                            val arrayNameCamel =
                                "${Array(nameParts.size) { nameParts[it].capitalize() }.joinToString("")}${fontSize}px"
                            appendFontFile(
                                "package com.rudolas.mia.lcdst7920.fonts;\n" +
                                        "\npublic class $arrayNameCamel {" +
                                        "\n    private  static final int[][] charsPixels = {",
                                "package com.rudolas.mia.lcdst7920.fonts\n" +
                                        "\nclass $arrayNameCamel {\n" +
                                        "    companion object {\n" +
                                        "        val font = FontItem( // FONT ${fontSize}px $fontName.ttf\n" +
                                        "            \"${arrayName.toUpperCase()}\",\n" +
                                        "            charBytes = arrayOf("
                            )
                            pixels = Array(fontSize * 3) { Array(fontSize * 3) { false } }

                            val downloadDir = File(Environment.getExternalStorageDirectory(), "Download")
                            val fontsDir = File(downloadDir, "Fonts")
                            if (!fontsDir.exists() && !fontsDir.mkdir()) {
                                logMsg("CANNOT ACCESS KeywordsDownload directory")
                                return
                            }
                            fontFiles = Array(2) {
                                File(fontsDir, "$arrayNameCamel.${getLangPrefix(it)}").apply {
                                    if (!exists()) {
                                        logMsg("Font file to be created $absolutePath")
                                    }
                                }
                            }
                        }

                        val divider = fontParams.divider
                        fontCharTextView.draw(Canvas(charBitmap))
                        // ignore char 64 line feed
                        val bitmapWidth = if (charIndex == 64) 1 else charBitmap.width / divider
                        val bitmapHeight = (charBitmap.height - fontParams.bottomOffset) / divider
                        widthsArray[charIndex] = bitmapWidth

//                        val hasNoTopOffset = fontParams.topOffset == 0
                        if (/*hasNoTopOffset &&*/ charIndex != 64) {
                            pixelHeightMax = max(bitmapHeight, pixelHeightMax)
                            pixelWidthMax = max(bitmapWidth, pixelWidthMax)
//                            logMsg("SK: Max bitmap $pixelWidthMax x $pixelHeightMax '${fontCharTextView.text}'")
                        }
                        val topOffset = fontParams.topOffset
                        for (y in topOffset until bitmapHeight * divider step divider) {
                            var byte = 0
                            for (x in 0 until bitmapWidth * divider step divider) {
                                val isPixelOn = charBitmap.getPixel(x, y) != 0
                                val pixelX = x / divider
                                val pixelY = y / divider
                                if (/*hasNoTopOffset &&*/ isPixelOn && pixelX < pixels.size && pixelY < pixels.size && !pixels[pixelY][pixelX]) {
                                    pixels[pixelY][pixelX] = true
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

                        if (isEmpty) {
                            val appendix = ", // blank font char '$charText' $hexAscii"
                            appendFontFile(
                                "            {}$appendix",
                                "                IntArray(0)$appendix"
                            )
                        } else {
                            val appendix = "${if (isNotLastChar) "," else ""
                            }$spacer // [$charIndex] ${bitmapWidth}x${bitmapHeight - topOffset / divider} '$charText' $hexAscii"
                            appendFontFile(
                                "            {$rowBytes}$appendix",
                                "                intArrayOf($rowBytes)$appendix"
                            )
                        }
                        //fontTexts.getChildAt(3).background = BitmapDrawable(resources, charBitmap)
                        charBitmap.recycle() // recycle manually if not assigned to imageView

                        when {
                            ++charIndex < latinCharacters.length -> assignLatinCharToRender()
                            else -> {
                                writeArrayEnd()
                                charIndex = 0
                                val isContinuousRendering = fontIndex < FONT_PARAMS.size - 1
                                logMsg("SK: Render [$fontIndex] $fontName ${fontParams.fontSize.toInt()}px ${if (isContinuousRendering) "CONTINUOUS" else "SINGLE"} $charText")
                                // continue to render next view
                                if (isContinuousRendering) {
                                    updateFontPreview(++fontIndex, "")
                                    assignLatinCharToRender()
                                } else {
                                    fontTexts.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                    fontLatinCharsView.text = skChars
                                    setCharTextSize(5f)
                                }
                            }
                        }
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

                    private fun appendFontFile(line: String) {
                        appendFontFile(line, line)
                    }

                    /**
                     * appends string line to output file string builder - to speed up file generation.
                     * It will be flushed to physical sdcard file at the end of rendering
                     */
                    private fun appendFontFile(lineJava: String, lineKotlin: String) {
                        stringFileBuilders[LANG_JAVA].append(lineJava).append("\n")
                        stringFileBuilders[LANG_KOTLIN].append(lineKotlin).append("\n")
//                        logMsg("SK: $line")
                    }

                    /**
                     * appends hex form of provided ascii char into a given string builder
                     */
                    private fun appendHexString(stringBuilder: StringBuilder, ascii: Int) {
                        val hexString = Integer.toHexString(ascii).toUpperCase()
                        stringBuilder.append("0x")
                            .append(if (hexString.length == 1) "0" else "")
                            .append(hexString)
                    }

                    /**
                     * assigns ascii char to preview text view for next rendering loop
                     */
                    private fun assignLatinCharToRender() {
                        fontCharTextView.text = latinCharacters[charIndex].toString()
                    }

                    /**
                     * appends end of array at an font chars map rendering or font widths array generation
                     * Following parts are added
                     *
                     *  - end of array
                     *  - java FontItem FONT object
                     *  - Max character bitmap info and
                     *  - Mass heat matrix text preview from all characters rendered into one map
                     */
                    private fun writeArrayEnd() {
                        appendFontFile(
                            "    };\n    private static final int[] widths = {",
                            "            ),\n            widths = intArrayOf("
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
                                val appendix =
                                    "$stringPreviewBuilder // '$firstChar'..'${latinCharacters[i]}' $stringHexBuilder"
                                appendFontFile("            $appendix", "                $appendix")
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
                            appendFontFile("            $appendix", "                $appendix")
                            stringPreviewBuilder.clear()
                        }
                        val fontParams = FONT_PARAMS[fontIndex]
                        appendFontFile(
                            "    };\n\n    public static final FontItem FONT = new FontItem(\n" +
                                    "            \"${fontParams.fontName.toUpperCase()}_${fontParams.fontSize.toInt()}PX\",\n" +
                                    "            charsPixels,\n" +
                                    "            widths\n" +
                                    "    );\n}",
                            "            )\n        )\n    }\n}"
                        )

                        appendFontFile(
                            "// Max Character Bitmap $pixelWidthMax x $pixelHeightMax ${
                            if (fontParams.divider > 1) "Divider ${fontParams.divider}" else ""
                            } Offsets [Top=${fontParams.topOffset}, Bottom=${fontParams.bottomOffset}]" +
                                    "\n// Mass Matrix - merged text preview from all characters rendered into one map"
                        )
                        for (y in 0 until min(pixelHeightMax, pixels.size)) {
                            for (x in 0 until min(pixelWidthMax, pixels.size)) {
                                stringPreviewBuilder.append(if (pixels[y][x]) '#' else '.')  // preview '#' as pixel on and '.' as an empty pixel
                            }
                            appendFontFile("// Mass Matrix $stringPreviewBuilder $y")
                            stringPreviewBuilder.clear()
                        }
                        // write file content builder to output file
                        for (i in fontFiles.indices) {
                            fontFiles[i].sink(false).buffer().use {
                                it.write(stringFileBuilders[i].toString().encodeUtf8())
                            }
                            stringFileBuilders[i].clear()
                        }
                    }
                }
            })
    }

    /**
     * assigns requested font size into font char text view used for rendering
     */
    private fun setCharTextSize(textSize: Float) {
        fontCharTextView.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            textSize
        )
    }

    /**
     * updates font character preview to specified font ascii char and/or to a given font size
     */
    private fun updateFontPreview(
        index: Int,
        latinCharacters: String,
        toCheckBitmaps: Boolean = false,
        forceCreate: Boolean = false
    ) {
        val fontParams = FONT_PARAMS[index]
//        if (fontParams.fontSize.toInt() == 1) {
//            updateFontPreview(++fontIndex, latinCharacters)
//            return
//        }
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

    /**
     * show dialog to choose which font to scroll to
     */
    private fun showGoToFontDialog() {
        AlertDialog.Builder(this)
            .setAdapter(
                DialogListAdapter(
                    this,
                    R.layout.font_item,
                    R.id.fontItemName,
                    FONT_PARAMS.mapIndexed { index, fontParams -> "$index. ${fontParams.fontName} ${fontParams.fontSize.toInt()}px" }.toMutableList()
                )
            ) { _, position ->
                if (isDetailIntentAction()) {
                    fontIndex = position
                    previewOrGeneratePixelCodeArrays(false)
                } else {
                    fontsScroller.smoothScrollTo(
                        0,
                        fontTexts.getChildAt(min(position, fontTexts.childCount) * 4).bottom
                    )
                }
            }
            .show()
    }

    private class DialogListAdapter(
        context: Context,
        resource: Int,
        textViewResourceId: Int,
        objects: MutableList<String>
    ) : ArrayAdapter<String>(context, resource, textViewResourceId, objects) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val fontNameFrame = super.getView(position, convertView, parent) as ViewGroup
            (fontNameFrame.getChildAt(0) as TextView).typeface =
                context.resources.getFont(FONT_PARAMS[position].fontRes)

            return fontNameFrame
        }
    }

    private fun inflateTextView(@LayoutRes layoutResId: Int = R.layout.text_view) =
        layoutInflater.inflate(layoutResId, fontTexts, false) as TextView

    /**
     * rendering related data for each true type font found within res/font
     * In case of adding a new true type font resource, an instance of this object is recommended to be add into FONT_PARAMS
     * to enable font rendering and preview
     */
    private data class FontParams(
        internal var divider: Int = 1,
        internal var topOffset: Int = 0,
        internal var bottomOffset: Int = 0,
        internal var fontSize: Float,
        internal var fontRes: Int,
        internal var fontName: String
    )

    companion object {

        /**
         * filtered fonts array, used internally for testing
         * 43 graph , 52 led calculator, 4 aerxtabs   28 fonts
         */
        private val fonts = arrayOf(
            4, 6, 10, 18, 23, 26, 31, 36, 37, 40, 42, 43, 49, 52, 61, 65, 69, 71, 75, 79, 80, 81, 82, 86 /*poco*/,
            88, 91, 92, 123
        )

        /**
         * array of rendering related data for all provided and supported font resources
         * for all new font resources, here the font related data must be added or altered
         */
        private val FONT_PARAMS = arrayOf(
            FontParams(
                fontSize = 20f, fontRes = R.font.adbxtra, fontName = "adbxtra",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 18f, fontRes = R.font.addwb_, fontName = "addwb",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),
            FontParams(fontSize = 20f, fontRes = R.font.advanced_pixel_7, fontName = "advanced_pixel_7"),
            FontParams(
                fontSize = 16f, fontRes = R.font.aerx_font, fontName = "aerx_font",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
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
                fontSize = 32f, fontRes = R.font.animal_crossing_wild_world,  // 32f
                fontName = "animal_crossing_wild_world",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),
            FontParams(fontSize = 16f, fontRes = R.font.babyblue, fontName = "babyblue"),
            FontParams(fontSize = 16f, fontRes = R.font.bit_light10_srb, fontName = "bit_light10_srb"), // poor
            FontParams(fontSize = 28f, fontRes = R.font.bitdust1, fontName = "bitdust1"),
            FontParams(fontSize = 28f, fontRes = R.font.bitlow, fontName = "bitlow"),
            FontParams(
                fontSize = 41f, fontRes = R.font.bitx_map_font_tfb, fontName = "bitx_map_font_tfb",
                divider = 1, topOffset = 10, bottomOffset = 0
            ),
            FontParams(fontSize = 9f, fontRes = R.font.blau7pt, fontName = "blau7pt"), // wrong font
            FontParams(fontSize = 20f, fontRes = R.font.bm_mini, fontName = "bm_mini"),
            FontParams(fontSize = 30f, fontRes = R.font.bmhaa, fontName = "bmhaa"),
            FontParams(
                fontSize = 27f, fontRes = R.font.bncuword, fontName = "bncuword",
                divider = 1, topOffset = 5, bottomOffset = 6
            ),
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
            FontParams(fontSize = 16f, fontRes = R.font.emulator, fontName = "emulator"), // 1px
            FontParams(fontSize = 8f, fontRes = R.font.endlesstype, fontName = "endlesstype"),
            FontParams(fontSize = 16f, fontRes = R.font.enter_command, fontName = "enter_command"),
            FontParams(fontSize = 41f, fontRes = R.font.everyday, fontName = "everyday"),
            FontParams(fontSize = 38f, fontRes = R.font.fffac, fontName = "fffac"),
            FontParams(fontSize = 8f, fontRes = R.font.fleftex_m, fontName = "fleftex_m"),
            FontParams(
                fontSize = 16f, fontRes = R.font.font15x5, fontName = "font15x5",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),
            FontParams(
                fontSize = 16f, fontRes = R.font.font2a03_memesbruh03, fontName = "font2a03_memesbruh03",
                divider = 1, topOffset = 1, bottomOffset = 1
            ),
            FontParams(fontSize = 16f, fontRes = R.font.font712_serif, fontName = "font712_serif"),
            FontParams(
                fontSize = 24f, fontRes = R.font.font7x5, fontName = "font7x5",
                divider = 1, topOffset = 0, bottomOffset = 0
            ),  //16px
            FontParams(fontSize = 7f, fontRes = R.font.font_8_bit_fortress, fontName = "font_8_bit_fortress"),
            FontParams(
                fontSize = 16f, fontRes = R.font.free_pixel, fontName = "free_pixel",
                divider = 1, topOffset = 0, bottomOffset = 1
            ),
            FontParams(fontSize = 52f, fontRes = R.font.fruits, fontName = "fruits"),
            FontParams(fontSize = 40f, fontRes = R.font.grand9k_pixel, fontName = "grand9k_pixel"),
            FontParams(fontSize = 8f, fontRes = R.font.graph_35_pix, fontName = "graph_35_pix"),
            FontParams(fontSize = 41f, fontRes = R.font.graphicpixel, fontName = "graphicpixel"),
            FontParams(fontSize = 16f, fontRes = R.font.grudb_lit, fontName = "grudb_lit"),  // 1px
            FontParams(fontSize = 13f, fontRes = R.font.hello_world, fontName = "hello_world"),  // 52px
            FontParams(
                fontSize = 16f, fontRes = R.font.heytext, fontName = "heytext",
                divider = 1, topOffset = 2, bottomOffset = 0
            ),
            FontParams(fontSize = 20f, fontRes = R.font.homespun, fontName = "homespun"), // 60px
            FontParams(fontSize = 16f, fontRes = R.font.igiari, fontName = "igiari"),
            FontParams(fontSize = 52f, fontRes = R.font.itty, fontName = "itty"),
            FontParams(fontSize = 16f, fontRes = R.font.jd_lcd_rounded, fontName = "jd_lcd_rounded"),  // 1px
            FontParams(
                fontSize = 20f, fontRes = R.font.led_calculator, fontName = "led_calculator",
                divider = 1, topOffset = 6, bottomOffset = 2
            ),
            FontParams(fontSize = 48f, fontRes = R.font.lexipa, fontName = "lexipa"),
            FontParams(fontSize = 16f, fontRes = R.font.lilliput_steps, fontName = "lilliput_steps"),  // 1px
            FontParams(fontSize = 52f, fontRes = R.font.lqdkdz_nospace, fontName = "lqdkdz_nospace"),
            FontParams(fontSize = 36f, fontRes = R.font.manual_display, fontName = "manual_display"),
            FontParams(fontSize = 40f, fontRes = R.font.mega_man_zx, fontName = "mega_man_zx"),
            FontParams(fontSize = 16f, fontRes = R.font.mini_kylie, fontName = "mini_kylie"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.mini_power, fontName = "mini_power"),  // 1px
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
            FontParams(fontSize = 16f, fontRes = R.font.moonracr, fontName = "moonracr"),  // 1px
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
            FontParams(
                fontSize = 20f, fontRes = R.font.pix_l, fontName = "pix_l",
                divider = 2, topOffset = 0, bottomOffset = 0
            ), //1px
            FontParams(fontSize = 16f, fontRes = R.font.pixel_block_bb, fontName = "pixel_block_bb"),  // 1px
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
            FontParams(
                fontSize = 21f, fontRes = R.font.rntg_larger, fontName = "rntg_larger",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),  //1px
            FontParams(
                fontSize = 20f, fontRes = R.font.root_square, fontName = "root_square",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),  //1px
            FontParams(fontSize = 40f, fontRes = R.font.rosesare_ff0000, fontName = "rosesare_ff0000"), //1px
            FontParams(
                fontSize = 20f,
                fontRes = R.font.rse_handwriting_pixel,
                fontName = "rse_handwriting_pixel"
            ), //1px
            FontParams(fontSize = 16f, fontRes = R.font.rtt_redstar_8, fontName = "rtt_redstar_8"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.savior1, fontName = "savior1"),  //1px
            FontParams(
                fontSize = 16f, fontRes = R.font.scifibit, fontName = "scifibit",
                divider = 2, topOffset = 0, bottomOffset = 0
            ),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.sevastopol_interface, fontName = "sevastopol_interface"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.sg04, fontName = "sg04"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.sgk075, fontName = "sgk075"),  //1px
            FontParams(fontSize = 8f, fontRes = R.font.silly_pixel, fontName = "silly_pixel"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.smt_devil_survivor, fontName = "smt_devil_survivor"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.somybmp01_7, fontName = "somybmp01_7"),  // 1px
// FontParams(fontSize = 16f, fontRes = R.font.sprite_comic, fontName = "sprite_comic"),
            FontParams(fontSize = 16f, fontRes = R.font.st01r, fontName = "st01r"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.supernat1001, fontName = "supernat1001"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.tama_mini02, fontName = "tama_mini02"),  // 1px
            FontParams(
                fontSize = 25f, fontRes = R.font.techkr, fontName = "techkr",
                divider = 1, topOffset = 12, bottomOffset = 0
            ),
            FontParams(fontSize = 16f, fontRes = R.font.teeny_pix, fontName = "teeny_pix"),  // 1px
            FontParams(fontSize = 20f, fontRes = R.font.thin_pixel_7, fontName = "thin_pixel_7"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.threebyfive_memesbruh03, fontName = "threebyfive_memesbruh03"),  // 1px
            FontParams(fontSize = 30f, fontRes = R.font.tiny, fontName = "tiny"),  //1px
            FontParams(fontSize = 16f, fontRes = R.font.tiny_unicode, fontName = "tiny_unicode"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.tloz_minish_cap_a_link_to_the_past_four_sword,
                fontName = "tloz_minish_cap_a_link_to_the_past_four_sword"
            ),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.tloz_phantom_hourglass,
                fontName = "tloz_phantom_hourglass"
            ),  //1px
            FontParams(fontSize = 8f, fontRes = R.font.uni05_53, fontName = "uni05_53"),
            FontParams(fontSize = 9f, fontRes = R.font.volter28goldfish_29, fontName = "volter28goldfish_29"),  //1px
            FontParams(fontSize = 8f, fontRes = R.font.zepto_regular, fontName = "zepto_regular"), //1px
            //  *******************************************
            //   NEW   FONTS  1
            //  *******************************************
            FontParams(fontSize = 34f, fontRes = R.font.a_futuricalt_thin, fontName = "a_futuricalt_thin"),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.accius_t_ot_light_condensed,
                fontName = "accius_t_ot_light_condensed"
            ),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.aclonica, fontName = "aclonica"),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.advanced_sans_serif_7,
                fontName = "advanced_sans_serif_7"
            ),    // 1px
            FontParams(fontSize = 16f, fontRes = R.font.air_conditioner, fontName = "air_conditioner"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.amita_regular, fontName = "amita_regular"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.anyway_light, fontName = "anyway_light"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.apple_chancery, fontName = "apple_chancery"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.arimo_regular, fontName = "arimo_regular"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.artcraft_urw_treg, fontName = "artcraft_urw_treg"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.augie, fontName = "augie"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.baksheesh_thin, fontName = "baksheesh_thin"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.boring_boring, fontName = "boring_boring"),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.calligraffitti_regular,
                fontName = "calligraffitti_regular"
            ),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.century_gothic, fontName = "century_gothic"),  // 1px
            FontParams(fontSize = 16f, fontRes = R.font.century_regular, fontName = "century_regular"),  // 1px
            FontParams(
                fontSize = 16f,
                fontRes = R.font.century_schoolbook_normal,
                fontName = "century_schoolbook_normal"
            ),  // 41px
            FontParams(fontSize = 16f, fontRes = R.font.centuryschool, fontName = "centuryschool"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.chalet_comprime_cologne_sixty,
                fontName = "chalet_comprime_cologne_sixty"
            ),
            FontParams(
                fontSize = 31f,
                fontRes = R.font.chalet_london_nineteen_seventy,
                fontName = "chalet_london_nineteen_seventy"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.chalet_newyork_nineteen_seventy,
                fontName = "chalet_newyork_nineteen_seventy"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.chalk_dust, fontName = "chalk_dust"),
            FontParams(fontSize = 16f, fontRes = R.font.chancery, fontName = "chancery"),
            FontParams(fontSize = 16f, fontRes = R.font.chancery_regular, fontName = "chancery_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.cherry_cream_soda, fontName = "cherry_cream_soda"),
            FontParams(fontSize = 16f, fontRes = R.font.chevin_medium, fontName = "chevin_medium"),
            FontParams(fontSize = 16f, fontRes = R.font.chewy, fontName = "chewy"),
            FontParams(fontSize = 16f, fontRes = R.font.classico_urw_t_ot, fontName = "classico_urw_t_ot"),
            FontParams(fontSize = 16f, fontRes = R.font.coming_soon, fontName = "coming_soon"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.commerce_black_condensed_ssi_bold_condensed,
                fontName = "commerce_black_condensed_ssi_bold_condensed"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.concetta, fontName = "concetta"),
            FontParams(fontSize = 16f, fontRes = R.font.conformity_normal, fontName = "conformity_normal"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.context_light_condensed_ssi_light_condensed,
                fontName = "context_light_condensed_ssi_light_condensed"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.context_reprise_condensed_ssi_condensed,
                fontName = "context_reprise_condensed_ssi_condensed"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.context_reprise_thin_ssi_thin,
                fontName = "context_reprise_thin_ssi_thin"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.context_semi_condensed_ssi_semi_condensed,
                fontName = "context_semi_condensed_ssi_semi_condensed"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.courier_std_medium, fontName = "courier_std_medium"),
            FontParams(fontSize = 16f, fontRes = R.font.cousine_regular, fontName = "cousine_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.crafty_girls, fontName = "crafty_girls"),
            FontParams(fontSize = 16f, fontRes = R.font.creepster_regular, fontName = "creepster_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.critical, fontName = "critical"),
            FontParams(fontSize = 16f, fontRes = R.font.crocs, fontName = "crocs"),
            FontParams(fontSize = 16f, fontRes = R.font.crushed, fontName = "crushed"),
            FontParams(fontSize = 16f, fontRes = R.font.d3_cubism, fontName = "d3_cubism"),
            FontParams(fontSize = 16f, fontRes = R.font.damned_architect, fontName = "damned_architect"),
            FontParams(fontSize = 16f, fontRes = R.font.darling_nikki, fontName = "darling_nikki"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.dateline_condensed_plain,
                fontName = "dateline_condensed_plain"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.daves_hand_regular, fontName = "daves_hand_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.david_sans_condensed, fontName = "david_sans_condensed"),
            FontParams(fontSize = 16f, fontRes = R.font.dawn_castle, fontName = "dawn_castle"),
            FontParams(fontSize = 16f, fontRes = R.font.daxline_pro_thin, fontName = "daxline_pro_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.defatted_milk_condensed, fontName = "defatted_milk_condensed"),
            FontParams(fontSize = 16f, fontRes = R.font.dejavu_sans, fontName = "dejavu_sans"),
            FontParams(fontSize = 16f, fontRes = R.font.dejavu_sans_condensed, fontName = "dejavu_sans_condensed"),
            FontParams(fontSize = 16f, fontRes = R.font.dejavu_sans_mono, fontName = "dejavu_sans_mono"),
            FontParams(fontSize = 16f, fontRes = R.font.dejavu_serif_condensed, fontName = "dejavu_serif_condensed"),
            FontParams(fontSize = 16f, fontRes = R.font.dekorator_sto, fontName = "dekorator_sto"),
            FontParams(fontSize = 16f, fontRes = R.font.delitsch_antiqua, fontName = "delitsch_antiqua"),
            FontParams(fontSize = 16f, fontRes = R.font.desert_crypt, fontName = "desert_crypt"),
            FontParams(fontSize = 16f, fontRes = R.font.desert_dog_hmk, fontName = "desert_dog_hmk"),
            FontParams(fontSize = 16f, fontRes = R.font.diablo_heavy, fontName = "diablo_heavy"),
            FontParams(fontSize = 16f, fontRes = R.font.dialog_regular, fontName = "dialog_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.diamond_gothic, fontName = "diamond_gothic"),
            FontParams(fontSize = 16f, fontRes = R.font.diegolo, fontName = "diegolo"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.digital_serial_regular_db,
                fontName = "digital_serial_regular_db"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.din, fontName = "din"),
            FontParams(fontSize = 16f, fontRes = R.font.din_light, fontName = "din_light"),
            FontParams(fontSize = 16f, fontRes = R.font.din_light_alternate, fontName = "din_light_alternate"),
            FontParams(fontSize = 16f, fontRes = R.font.din_pro_light, fontName = "din_pro_light"),
            FontParams(fontSize = 16f, fontRes = R.font.din_pro_regular, fontName = "din_pro_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.disney_park, fontName = "disney_park"),
            FontParams(fontSize = 16f, fontRes = R.font.dn_manuscript, fontName = "dn_manuscript"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.dolores_cyrillic_regular,
                fontName = "dolores_cyrillic_regular"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.don_casio, fontName = "don_casio"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.donna_bodoni_aa_script_pdf,
                fontName = "donna_bodoni_aa_script_pdf"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.donna_bodoni_ce_script_pdf,
                fontName = "donna_bodoni_ce_script_pdf"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.doodle_kid, fontName = "doodle_kid"),
            FontParams(fontSize = 16f, fontRes = R.font.dot_matrix_normal, fontName = "dot_matrix_normal"),
            FontParams(fontSize = 16f, fontRes = R.font.dot_matrix_regular, fontName = "dot_matrix_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.draft_gothic_thin, fontName = "draft_gothic_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.draft_plate_condensed, fontName = "draft_plate_condensed"),
            FontParams(fontSize = 16f, fontRes = R.font.draftsman_normal, fontName = "draftsman_normal"),
            FontParams(fontSize = 16f, fontRes = R.font.dtl_prokyon_st_light, fontName = "dtl_prokyon_st_light"),
            FontParams(fontSize = 16f, fontRes = R.font.duc_de_berry, fontName = "duc_de_berry"),
            FontParams(fontSize = 16f, fontRes = R.font.dummy_boy, fontName = "dummy_boy"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.dzeragir_decorative_italic,
                fontName = "dzeragir_decorative_italic"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.elementric, fontName = "elementric"),
            FontParams(fontSize = 16f, fontRes = R.font.erin_go_bragh_condensed, fontName = "erin_go_bragh_condensed"),
            FontParams(fontSize = 16f, fontRes = R.font.everlicious, fontName = "everlicious"),
            FontParams(fontSize = 16f, fontRes = R.font.ft19_condensed_regular, fontName = "ft19_condensed_regular"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.ft24_extracondensed_medium,
                fontName = "ft24_extracondensed_medium"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.fujiyama2, fontName = "fujiyama2"),
            FontParams(fontSize = 16f, fontRes = R.font.gapstown, fontName = "gapstown"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.garamond_light_condensed_ssi_light_condensed,
                fontName = "garamond_light_condensed_ssi_light_condensed"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.gracie, fontName = "gracie"),
            FontParams(fontSize = 16f, fontRes = R.font.graphium_thin, fontName = "graphium_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.greenbeans_thin, fontName = "greenbeans_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.greymagus, fontName = "greymagus"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.grutchgrotesk_condensed_light,
                fontName = "grutchgrotesk_condensed_light"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.harry, fontName = "harry"),
            FontParams(fontSize = 16f, fontRes = R.font.harvey, fontName = "harvey"),
            FontParams(fontSize = 16f, fontRes = R.font.high_sign, fontName = "high_sign"),
            FontParams(fontSize = 16f, fontRes = R.font.hipchick, fontName = "hipchick"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.ibmplex_sans_condensed_light,
                fontName = "ibmplex_sans_condensed_light"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.ibmplex_sans_condensed_regular,
                fontName = "ibmplex_sans_condensed_regular"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.ibmplex_sans_condensed_thin,
                fontName = "ibmplex_sans_condensed_thin"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.inconsolata_regular, fontName = "inconsolata_regular"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.interstate_light_condensed,
                fontName = "interstate_light_condensed"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.jagged_dreams, fontName = "jagged_dreams"),
            FontParams(fontSize = 16f, fontRes = R.font.just_another_hand, fontName = "just_another_hand"),
            FontParams(fontSize = 16f, fontRes = R.font.kenzou, fontName = "kenzou"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.kepler_std_light_condensed_display,
                fontName = "kepler_std_light_condensed_display"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.kepler_std_light_semicondensed_caption,
                fontName = "kepler_std_light_semicondensed_caption"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.kepler_std_light_semicondensed_display,
                fontName = "kepler_std_light_semicondensed_display"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.kepler_std_semicondensed,
                fontName = "kepler_std_semicondensed"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.kktdmag, fontName = "kktdmag"),
            FontParams(fontSize = 16f, fontRes = R.font.lato_light, fontName = "lato_light"),
            FontParams(fontSize = 16f, fontRes = R.font.lato_regular, fontName = "lato_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.lavi, fontName = "lavi"),
            FontParams(fontSize = 16f, fontRes = R.font.lekton04_thin, fontName = "lekton04_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.liberation_mono_regular, fontName = "liberation_mono_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.maiden_orange, fontName = "maiden_orange"),
            FontParams(fontSize = 16f, fontRes = R.font.mcg, fontName = "mcg"),
            FontParams(fontSize = 16f, fontRes = R.font.montez_regular, fontName = "montez_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.nee, fontName = "nee"),
            FontParams(fontSize = 16f, fontRes = R.font.nk57_monospace_ex_lt, fontName = "nk57_monospace_ex_lt"),
            FontParams(fontSize = 16f, fontRes = R.font.nk57_monospace_ex_rg, fontName = "nk57_monospace_ex_rg"),
            FontParams(fontSize = 16f, fontRes = R.font.nk57_monospace_sc_lt, fontName = "nk57_monospace_sc_lt"),
            FontParams(fontSize = 16f, fontRes = R.font.nk57_monospace_se_rg, fontName = "nk57_monospace_se_rg"),
            FontParams(fontSize = 16f, fontRes = R.font.open_sans_condlight, fontName = "open_sans_condlight"),
            FontParams(fontSize = 16f, fontRes = R.font.open_sans_light, fontName = "open_sans_light"),
            FontParams(fontSize = 16f, fontRes = R.font.open_sans_regular, fontName = "open_sans_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.orbitron_bold_webfont, fontName = "orbitron_bold_webfont"),
            FontParams(fontSize = 16f, fontRes = R.font.orbitron_light, fontName = "orbitron_light"),
            FontParams(fontSize = 16f, fontRes = R.font.orbitron_light_webfont, fontName = "orbitron_light_webfont"),
            FontParams(fontSize = 16f, fontRes = R.font.orbitron_medium, fontName = "orbitron_medium"),
            FontParams(fontSize = 16f, fontRes = R.font.permanent_marker, fontName = "permanent_marker"),
            FontParams(fontSize = 16f, fontRes = R.font.pill_gothic_300mg_thin, fontName = "pill_gothic_300mg_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.quilline_script_thin, fontName = "quilline_script_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.rabiohead, fontName = "rabiohead"),
            FontParams(fontSize = 16f, fontRes = R.font.rancho_regular, fontName = "rancho_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.redressed, fontName = "redressed"),
            FontParams(fontSize = 16f, fontRes = R.font.ribeye_regular, fontName = "ribeye_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.roboto_condensed_light, fontName = "roboto_condensed_light"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.roboto_condensed_regular,
                fontName = "roboto_condensed_regular"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.roboto_light, fontName = "roboto_light"),
            FontParams(fontSize = 16f, fontRes = R.font.roboto_regular, fontName = "roboto_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.rochester_regular, fontName = "rochester_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.rocketscript, fontName = "rocketscript"),
            FontParams(fontSize = 16f, fontRes = R.font.sans_thin_thin, fontName = "sans_thin_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.satisfy_regular, fontName = "satisfy_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.schoolbell, fontName = "schoolbell"),
            FontParams(fontSize = 16f, fontRes = R.font.schwaben_alt_thin_thin, fontName = "schwaben_alt_thin_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.skinny, fontName = "skinny"),
            FontParams(fontSize = 16f, fontRes = R.font.slackey, fontName = "slackey"),
            FontParams(fontSize = 16f, fontRes = R.font.smokum_regular, fontName = "smokum_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.source_sans_pro_regular, fontName = "source_sans_pro_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.source_sans_pro_light, fontName = "source_sans_pro_light"),
            FontParams(fontSize = 16f, fontRes = R.font.speciale_lite, fontName = "speciale_lite"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.stint_ultra_condensed_regular,
                fontName = "stint_ultra_condensed_regular"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.sun, fontName = "sun"),
            FontParams(fontSize = 16f, fontRes = R.font.sunshiney, fontName = "sunshiney"),
            FontParams(fontSize = 16f, fontRes = R.font.syncopate_regular, fontName = "syncopate_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.thaiga_thin, fontName = "thaiga_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.tinos_regular, fontName = "tinos_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.ultra, fontName = "ultra"),
            FontParams(fontSize = 16f, fontRes = R.font.unisolv3c, fontName = "unisolv3c"),
            FontParams(fontSize = 16f, fontRes = R.font.unkempt_regular, fontName = "unkempt_regular"),
            FontParams(fontSize = 16f, fontRes = R.font.urw_grotesk_t_ot_light, fontName = "urw_grotesk_t_ot_light"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.urw_grotesk_t_ot_light_condensed,
                fontName = "urw_grotesk_t_ot_light_condensed"
            ),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.vag_rounded_light_ssi_thin,
                fontName = "vag_rounded_light_ssi_thin"
            ),
            FontParams(fontSize = 16f, fontRes = R.font.vag_rounded_std_thin, fontName = "vag_rounded_std_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.vag_rounded_thin, fontName = "vag_rounded_thin"),
            FontParams(fontSize = 16f, fontRes = R.font.walter_turncoat, fontName = "walter_turncoat"),
            FontParams(fontSize = 16f, fontRes = R.font.whatever, fontName = "whatever"),
            FontParams(fontSize = 16f, fontRes = R.font.wind, fontName = "wind"),
            FontParams(
                fontSize = 16f,
                fontRes = R.font.yacarena_ultra_personal_use,
                fontName = "yacarena_ultra_personal_use"
            )
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
        private const val LANG_KOTLIN = 0
        private const val LANG_JAVA = 1
        private const val LANG_C = 2
        private const val LANG_PYTHON = 3

        private const val ACTION_DETAIL = "ACTION_DETAIL"

        /**
         * supported basic ascii characters
         */
        private val ASCII_LATIN_RANGE1 = 32..126    // space .. ~
        /**
         * supported extended latin ascii chars
         * optionally additional greek and coptic, armenian, cyrillic, hebrew or arabic ASCII subsets could be enabled
         */
        private val ASCII_LATIN_RANGE2 = 161..382 // ¡ .. À .. ž
        private val ASCII_LATIN_COUNT =
            ASCII_LATIN_RANGE1.last - ASCII_LATIN_RANGE1.first + ASCII_LATIN_RANGE2.last - ASCII_LATIN_RANGE2.first + 2
        //        val charsToShowAsBitmaps = "8"
//        private const val charsToShowAsBitmaps = "8Ź"
        /**
         * ascii characters used for bitmap chars preview for font all font sizes from range 5px .. 64px
         * usually fonts are rendered for font sizes up to 24px, bigger fonts izes lead to excessive code generation for target languages.
         * To avoid font bytes code compilation troubles, e.g. java code too large,
         * keep font sizes to match native font sizes or as low as possible to render non excessive bitmap sizes.
         */
        private const val charsToShowAsBitmaps = "ýô8Ź0#"
        private const val skChars = "8Ź!01#\$/@QÁŽČčĎŢţŤťáäýóô"
//        private const val charsToShowAsBitmaps = "8Ź!01#\$/@QÁŽČčĎŢţŤťáäýóôaefghijklmnpqrwxy"

        private fun logMsg(msg: String) = android.util.Log.d("FontRenderer", msg)
    }
}
