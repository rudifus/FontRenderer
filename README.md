# FontRenderer - android truetype font converter into java/kotlin pixel bytes code arrays for LCD/OLED displays

*FontRenderer* is android app to preview and convert available true type fonts with extended latin characters such as diacritics 
into code usable for showing text on LCD/OLED displays connected to Raspberry Pi or Android Things devices.

This Android Studio project contains 302 already included true type fonts added into android resource dir `/res/font`.

![Preview Screen](generated/screens/Crocs16px_1.webp?raw=true)
![Preview Screen](generated/screens/Crocs16px_4.webp?raw=true)
[More Fonts Preview Screens](generated/README.md)

## Mobile App Main Features

 * preview available true type fonts with extended latin characters such as diacritics.
 * preview selected ASCII chars in font sizes from 5px to 64 px to choose the best match for the native font size resolution.
 * render complete ascii characters map into byte pixels array for java/kotlin code use.
 * render 5 font preview bitmaps and store them as WEBP images into a phone sdcard.

## Actual supported ASCII characters ranges
 *  standard characters         ASCII `32..126     space .. ~`
 *  extended latin characters   ASCII `161..382    ¡ .. À .. ž`
 * optionally additional greek and coptic, armenian, cyrillic, hebrew or arabic ASCII range subsets could be enabled

*listed:*

 ```  !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~
  ¡¢£¤¥¦§¨©ª«¬­®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿĀāĂăĄą
  ĆćĈĉĊċČčĎďĐđĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħĨĩĪīĬĭĮįİıĲĳĴĵĶķĸĹĺĻļĽľĿŀŁłŃńŅņŇňŉŊŋŌōŎŏŐőŒœŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŧŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽž```

## Output kotlin/java array code
* The app output is merged into array of char pixels bytes: one for kotlin and one for java,
 C or python code to be supported later. 

* Default native font size for rendering is 16px and is recommended to be changed to best match a native font size.

* Result code arrays are stored in SDcard directory 
  *`/sdcard/Download/Fonts/`*    e.g. filename `TinyUnicode16px.kt`
  
Also log output in android logcat is available. Log lines prefix to filter is `SK:`, just legacy to original language characters set, SK for Slovak :)
To enable logcat log just uncomment the following `logMsg` code line in `appendFontFile` method.

 ```kotlin
 private fun appendFontFile(line: String) {
           stringFileBuilder.append(line).append("\n")
 //           logMsg("SK: $line")
 }
```

* Hint: To avoid compilation troubles with generated font bytes arrays, e.g. java code too large, keep the render font size as low as possible to match native font size and to render non excessive bitmaps.
### Sample output

There are generated pixel bytes for each character from supported ASCII range per row. Bytes width and height corresponds to rendered native true type font size.
   e.g 6x12 pixels matrix corresponds to the rendered char converted into 12 hexa bytes as height with 6 pixels as width in row, e.g.

```kotlin
  000000 [0]
  000000 [1]
  000000 [2]
  011100 [3]
  100010 [4]
  011100 [5]
  100010 [6]
  100010 [7]
  011100 [8]
  000000 [9]
  000000 [10]
  000000 [11]
```

output kotlin code sample:

```kotlin
import fonts;

class TinyUnicode16px {
    companion object {
        val font = FontItem( // FONT 16px tiny_unicode.ttf
            "TINY_UNICODE_16PX",
            charBytes =  = arrayOf( // FONT 16px tiny_unicode.ttf
                intArrayOf(0x00, 0x00, 0x00, 0x1C, 0x22, 0x1C, 0x22, 0x22, 0x1C, 0x00, 0x00, 0x00), // 6x12 '8' 0x38
                intArrayOf(0x00, 0x00, 0x00, 0x08, 0x08, 0x08, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00), // 6x12 '!' 0x21
                ...
            ),
            widths = intArrayOf(      //  rendered ascii char pixels width, mono fonts have fixed size chars
                5,2,4,6,5,4,6,2,3,3,           // ' '..')' 0x20
                4,4,3,4,2,4,5,3,5,5,           // '*'..'3' 0x2A
                5,5,5,5,5,5,2,3,3,5,           // '4'..'=' 0x34
                ...
            )
        )
    }
}
// Max Bitmap 14 x 13, Offsets [Top=0, Bottom=0] 
// Mass Matrix - merged text preview from all characters rendered into one map
// Mass Matrix ############## 0
// Mass Matrix ############## 1
// Mass Matrix ############## 2
// Mass Matrix ############## 3
// Mass Matrix ############## 4
// Mass Matrix ############## 5
// Mass Matrix ############## 6
// Mass Matrix ############## 7
// Mass Matrix ############## 8
// Mass Matrix ############## 9
// Mass Matrix ############## 10
// Mass Matrix ############.. 11
// Mass Matrix ##########.... 12
```
