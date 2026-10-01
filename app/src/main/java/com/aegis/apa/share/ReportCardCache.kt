package com.aegis.apa.share

import java.io.File

object ReportCardCache {
    fun prepareTarget(directory: File, nowMillis: Long = System.currentTimeMillis()): File {
        directory.mkdirs()
        directory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.name.startsWith("APA-") && file.extension == "png" }
            .forEach(File::delete)
        return File(directory, "APA-$nowMillis.png")
    }
}
