package io.github.yurmont.yapetracker

import com.google.mlkit.vision.text.Text

/** OCR lines sorted top to bottom, then left to right. The parser copes with any order anyway. */
fun Text.linesInReadingOrder(): List<String> =
    textBlocks
        .flatMap { it.lines }
        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
        .map { it.text }
