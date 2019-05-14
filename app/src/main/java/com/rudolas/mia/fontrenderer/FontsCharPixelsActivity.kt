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
import android.support.annotation.LayoutRes
import android.support.v7.app.AlertDialog
import android.util.TypedValue
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.AfterPermissionGranted
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private var fixedThreadPool: ExecutorService? = null
    private var onGlobalLayoutListenerBitmapsCheck: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var fontIndex = 0
    private var charIndex = 0
    private lateinit var fontCharTextView: TextView
    private lateinit var fontTitleTextView: TextView
    private lateinit var fontLatinCharsView: TextView
    private var bitmapsLayouts: Array<LinearLayout>? = null
    private val fontRender = FontRender()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            fontIndex = savedInstanceState.getInt("fontIndex")
        }

        if (isDetailIntentAction()) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            previewImage.visibility = View.VISIBLE
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

    override fun onStop() {
        super.onStop()
        fixedThreadPool?.shutdown()
        //        logMsgWithNumber("onDestroy Shutdown", 0);
        try {
            fixedThreadPool?.awaitTermination(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            //
        }
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
//            R.id.menuPreview -> checkPreviewFontBitmap().let { true }
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
            val detailIntentAction = isDetailIntentAction()
            if (detailIntentAction) {
                if (fixedThreadPool == null) {
                    fixedThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1)
                }
            }
            previewOrGeneratePixelCodeArrays(!detailIntentAction)
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
        val latinCharactersString = fontRender.getExtendedLatinCharactersString()
        fontLatinCharsView.text = latinCharactersString
        setCharTextSize(getActFontParams().fontSize)
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
            generateFontCharactersCodeArray(true)
        }
    }

    /**
     * preview latin ascii chars for all available fonts from /res/font directory
     */
    private fun previewFontCharactersCodeArray() {
        //        val fontChars = "abcdefghijklmnoprqstuvwxyz ABCDEFGHIJKLMNOPRQSTUVWXYZ \n1234567890_+=:.,;/!?<>{}[]()"

        val latinCharacters = fontRender.getExtendedLatinCharactersString()
        for (index in 0 until fontRender.getFontsCount()) {
            updateFontPreview(index, latinCharacters, forceCreate = true)
        }
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
        toRender: Boolean = false
//        toGenerateCharPixelsPreview: Boolean = false
    ) {
        val latinCharacters = fontRender.getExtendedLatinCharactersString()

        val toCreate = fontTexts.childCount == 0
        updateFontPreview(fontIndex, latinCharacters, toCheckBitmaps, toCreate)

        charIndex = 0
//        logMsg("SK: Generate ${latinCharacters.length} start charIndex $charIndex")
        if (toCreate || !toRender && onGlobalLayoutListenerBitmapsCheck == null || toRender) {
            addGlobalLayoutListener(latinCharacters, toCheckBitmaps)
        }
    }

    /**
     * global layout listener used to await char layout formatting according to requested font type and font size.
     * It loops through either all font sizes from 5px until 64px or supported ascii font chars
     * In each font char rendering loop pixels bytes for all supported target languages are generated.
     *
     * @param latinCharacters string as an array of latin ascii chars to render
     * @param toCheckBitmaps true to preview 5px..64px font sizes into rendered char bitmaps, otherwise false to avoid bitmaps rendering
     * //@param toGenerateCharPixelsPreview true to generate text form character pixels map , where preview '#' as pixel on and '.' as an empty pixel
     *
     */
    private fun addGlobalLayoutListener(
        latinCharacters: String,
        toCheckBitmaps: Boolean
//        toGenerateCharPixelsPreview: Boolean
    ) {
        fontTexts.viewTreeObserver.addOnGlobalLayoutListener(
            if (toCheckBitmaps) {
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    private var bitmapCharIndex = 0

                    override fun onGlobalLayout() {
                        if (bitmapCharIndex >= charsToShowAsBitmaps.length) {
                            bitmapCharIndex = 0
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
                        (bitmapsLayouts?.get(BITMAPS_LINES * bitmapCharIndex + (diff / BITMAPS_COUNT))?.getChildAt((diff % BITMAPS_COUNT) * 2) as? ImageView)
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
                        } else if (++bitmapCharIndex < charsToShowAsBitmaps.length) {
                            fontCharTextView.text = charsToShowAsBitmaps[bitmapCharIndex].toString()
                        } else {
                            fontCharTextView.text = charsToShowAsBitmaps[0].toString()
                            //                            fontTexts.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                    }
                }.apply { onGlobalLayoutListenerBitmapsCheck = this }
            } else {
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
//                        if (charIndex < 1 || charIndex > 315) {
//                            logMsg("SK: GlobalLayout [$fontIndex] NO BitmapsCheck charIndex $charIndex ${
//                            if (fixedThreadPool == null) "Null" else "Threads1"} '${latinCharacters[charIndex]}'")
//                        }
                        val measuredWidth = fontCharTextView.measuredWidth
                        val measuredHeight = fontCharTextView.measuredHeight
                        val isEmpty = measuredWidth == 0 || measuredHeight == 0
                        val charBitmap = Bitmap.createBitmap(
                            if (isEmpty) 1 else measuredWidth,
                            if (isEmpty) 1 else measuredHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        fontCharTextView.draw(Canvas(charBitmap))

                        val fontIndex2 = fontIndex
                        val charIndex2 = charIndex
                        fixedThreadPool?.submit {
                            fontRender.processCharacterBitmap(
                                fontIndex2,
                                charIndex2,
                                charBitmap
                            ) {
                                runOnUiThread {
                                    previewImage.setImageBitmap(it)
//                                    previewImage.invalidate()
                                }
                            }
                        }

                        when {
                            ++charIndex < latinCharacters.length -> assignLatinCharToRender()
                            else -> {
                                charIndex = 0
                                val isContinuousRendering = fontIndex < fontRender.getFontsCount() - 1
                                // continue to render next view
                                if (isContinuousRendering) {
                                    updateFontPreview(++fontIndex, "")
                                    setCharTextSize(getActFontParams().fontSize)
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
                     * assigns ascii char to preview text view for next rendering loop
                     */
                    private fun assignLatinCharToRender() {
                        fontCharTextView.text = latinCharacters[charIndex].toString()
                    }
                }
            }
        )
    }

    /**
     * get font params for the current font index
     */
    private fun getActFontParams() = fontRender.getFontParams(fontIndex)

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
        val fontParams = fontRender.getFontParams(index)
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

            val frame = layoutInflater.inflate(R.layout.text_view_framed, fontFrame, false) as ViewGroup
            fontFrame.addView(frame)
            with(frame.getChildAt(0) as TextView) {
                fontCharTextView = this
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
//            with(inflateTextView(R.layout.text_view_centered)) {
//                //            background = BitmapDrawable(resources, textAsBitmap("ľščťžýáíéúäňôČŇĽĎŠŽŤ", 36f, Color.BLACK))
//                fontTexts.addView(this)
//            }
        }

        fontTitleTextView.text = "$index. ${fontParams.fontName} ${fontParams.fontSize.toInt()}px"
        val font = resources.getFont(fontParams.fontRes)
        fontCharTextView.typeface = font
        fontLatinCharsView.typeface = font
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
                    fontRender.getFontParamsList(),
                    fontRender
                )
            ) { _, position ->
                if (isDetailIntentAction()) {
                    fontIndex = position
                    previewOrGeneratePixelCodeArrays(false)
                } else {
                    fontsScroller.smoothScrollTo(
                        0,
                        fontTexts.getChildAt(min(position, fontTexts.childCount) * 2).top
                    )
                }
            }
            .show()
    }

    private class DialogListAdapter(
        context: Context,
        resource: Int,
        textViewResourceId: Int,
        objects: MutableList<String>,
        val fontRender: FontRender
    ) : ArrayAdapter<String>(context, resource, textViewResourceId, objects) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val fontNameFrame = super.getView(position, convertView, parent) as ViewGroup
            (fontNameFrame.getChildAt(0) as TextView).typeface =
                context.resources.getFont(fontRender.getFontParams(position).fontRes)

            return fontNameFrame
        }
    }

    private fun inflateTextView(@LayoutRes layoutResId: Int = R.layout.text_view) =
        layoutInflater.inflate(layoutResId, fontTexts, false) as TextView

    companion object {

        /**
         * filtered fonts array, used internally for testing
         * 43 graph , 52 led calculator, 4 aerxtabs   28 fonts
         */
        private val fonts = arrayOf(
            4, 6, 10, 18, 23, 26, 31, 36, 37, 40, 42, 43, 49, 52, 61, 65, 69, 71, 75, 79, 80, 81, 82, 86 /*poco*/,
            88, 91, 92, 123
        )

        private const val PERMISSION_WRITE_STORAGE = 4
        private const val SCALE = 4
        private const val BITMAPS_COUNT = 15
        private const val BITMAPS_LINES = 4

        private const val ACTION_DETAIL = "ACTION_DETAIL"

        /**
         * ascii characters used for bitmap chars preview for font all font sizes from range 5px .. 64px
         * usually fonts are rendered for font sizes up to 24px, bigger fonts sizes lead to excessive code generation for target languages.
         * To avoid font bytes code compilation troubles, e.g. java code too large,
         * keep font sizes to match native font sizes or as low as possible to render non excessive bitmap sizes.
         */
        private const val charsToShowAsBitmaps = "8ýô" // "ýô8Ź0#"
        private const val skChars = "8Ź!01#\$/@QÁŽČčĎŢţŤťáäýóô"

        private fun logMsg(msg: String) = android.util.Log.d("FontsCharPixelsActivity", msg)
    }
}