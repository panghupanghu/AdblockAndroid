package io.github.edsuns.adfilter.impl
import java.io.File
internal class BinaryDataStore(private val dir: File) {

    init {
        if (!dir.exists() && !dir.mkdirs()) {
            //Timber.v("BinaryDataStore: failed to create store dirs")
        }
    }

    fun hasData(name: String): Boolean = File(dir, name).exists()

    fun loadData(name: String): ByteArray =
        File(dir, name).readBytes()

    fun saveData(name: String, byteArray: ByteArray) {
        File(dir, name).writeBytes(byteArray)
    }

    fun clearData(name: String) {
        File(dir, name).delete()
    }
}