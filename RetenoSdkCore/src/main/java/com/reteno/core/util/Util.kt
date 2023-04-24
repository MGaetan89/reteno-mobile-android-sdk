package com.reteno.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.reteno.core.RetenoImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteStatement
import java.io.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun <T : Any> allElementsNull(vararg elements: T?) = elements.all { it == null }

fun <T : Any> allElementsNotNull(vararg elements: T?) = elements.all { it != null }

internal fun isGooglePlayServicesAvailable(): Boolean {
    val context = RetenoImpl.application
    val googleApiAvailability = GoogleApiAvailability.getInstance()
    val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
    return resultCode == ConnectionResult.SUCCESS
}

fun Context.getAppName(): String {
    val stringId = applicationInfo.labelRes
    val appName = if (stringId == 0) {
        applicationInfo.loadLabel(packageManager).toString()
    } else {
        getString(stringId)
    }
    /*@formatter:off*/ Logger.i(TAG, "getAppName(): ", "[", appName, "]")
    /*@formatter:on*/
    return appName
}

fun Context.getApplicationMetaData(): Bundle =
    if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        ).metaData
    } else {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
    }

fun Context.getResolveInfoList(intent: Intent): List<ResolveInfo?> =
    if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0)
        )
    } else {
        packageManager.queryIntentActivities(intent, 0)
    }

fun Context.queryBroadcastReceivers(intent: Intent): List<ResolveInfo?> =
    if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryBroadcastReceivers(
            intent,
            PackageManager.ResolveInfoFlags.of(0)
        )
    } else {
        packageManager.queryBroadcastReceivers(intent, 0)
    }


fun Bundle?.toStringVerbose(): String {
    if (this == null) {
        return "Bundle = [null]"
    }

    val stringBuilder = java.lang.StringBuilder()
    stringBuilder.append("Bundle: [")
    for (key in keySet()) {
        stringBuilder.append(key).append(" = ").append(get(key)).append("; ")
    }
    stringBuilder.delete(stringBuilder.length - 2, stringBuilder.length)
    stringBuilder.append("]")
    return stringBuilder.toString()
}

fun isRepeatableError(statusCode: Int?): Boolean {
    return statusCode !in 400..499
}

fun isNonRepeatableError(statusCode: Int?) = !isRepeatableError(statusCode)

fun isOsVersionSupported(): Boolean {
    val result = Build.VERSION.SDK_INT >= 26
    /*@formatter:off*/ Logger.i(TAG, "isOsVersionSupported(): Build.VERSION.SDK_INT = [", Build.VERSION.SDK_INT, "], result = [", result ,"]")
    /*@formatter:on*/
    return result
}

object Util {

    private var isDebugViewCashed: Boolean = false

    private val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneId.of("UTC"))

    init {
        CoroutineScope(IO).launch {
            val debugString = getSysProp(PROP_KEY_DEBUG_VIEW)
            isDebugViewCashed = debugString == PROP_VALUE_DEBUG_VIEW_ENABLE
        }
    }

    @JvmStatic
    fun readFromRaw(rawResourceId: Int): String? {
        val context = RetenoImpl.application
        /*@formatter:off*/ Logger.i(TAG, "readFromRaw(): ", "context = [" , context , "], rawResourceId = [" , rawResourceId , "]")
        /*@formatter:on*/

        val inputStream: InputStream = context.resources.openRawResource(rawResourceId)
        val outputStream = ByteArrayOutputStream()

        val buf = ByteArray(1024)
        var len: Int
        return try {
            while (inputStream.read(buf).also { len = it } != -1) {
                outputStream.write(buf, 0, len)
            }
            outputStream.toString()
        } catch (e: IOException) {
            /*@formatter:off*/ Logger.e(TAG, "readTextFile(): ", e)
            /*@formatter:on*/
            null
        } finally {
            outputStream.close()
            inputStream.close()
        }
    }

    @JvmStatic
    fun getCurrentTimeStamp(): String {
        val currentDate = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return currentDate.toString()
    }

    /**
     * To enable debugView mode run adb shell with the following command
     * adb shell setprop debug.com.reteno.debug.view enable
     * To disable change system property to any other one
     * adb shell setprop debug.com.reteno.debug.view disable
     */
    internal fun isDebugView(): Boolean {
        return isDebugViewCashed
    }

    fun ZonedDateTime.formatToRemote(): String {
        return formatter.format(this)
    }

    private fun getSysProp(key: String): String {
        val process: Process
        var propvalue = ""
        try {
            process = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            propvalue = reader.readLine()
            process.destroy()
        } catch (e: Exception) {
            propvalue = ""
        }
        return propvalue
    }

    suspend fun getDatabaseState(context: Context, dbPath: File): SqlStateEncrypt {
        if (dbPath.exists()) {
            return withContext(IO) {
                SQLiteDatabase.loadLibs(context)
                var db: SQLiteDatabase? = null
                try {
                    db = SQLiteDatabase.openDatabase(
                        dbPath.absolutePath,
                        "",
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )
                    db.version
                    SqlStateEncrypt.UNENCRYPTED
                } catch (e: Exception) {
                    SqlStateEncrypt.ENCRYPTED
                } finally {
                    db?.close()
                }
            }
        }
        return SqlStateEncrypt.DOES_NOT_EXIST
    }

    @Throws(IOException::class)
    suspend fun decrypt(ctxt: Context, originalFile: File, passphrase: ByteArray?) {
        withContext(IO) {
            SQLiteDatabase.loadLibs(ctxt)
            try {
                if (originalFile.exists()) {
                    val newFile = File.createTempFile(
                        "sqlcipherutils", "tmp",
                        ctxt.cacheDir
                    )
                    var db: SQLiteDatabase = SQLiteDatabase.openDatabase(
                        originalFile.absolutePath,
                        passphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null
                    )
                    val st: SQLiteStatement =
                        db.compileStatement("ATTACH DATABASE ? AS plaintext KEY ''")
                    st.bindString(1, newFile.absolutePath)
                    st.execute()
                    db.rawExecSQL("SELECT sqlcipher_export('plaintext')")
                    db.rawExecSQL("DETACH DATABASE plaintext")
                    val version: Int = db.version
                    st.close()
                    db.close()
                    db = SQLiteDatabase.openDatabase(
                        newFile.absolutePath, "",
                        null, SQLiteDatabase.OPEN_READWRITE
                    )
                    db.version = version
                    db.close()
                    originalFile.delete()
                    newFile.renameTo(originalFile)
                } else {
                    throw FileNotFoundException(originalFile.absolutePath + " not found")
                }
            } catch (ex: Exception) {

            }
        }
    }
}

const val TAG = "Util"
const val PROP_KEY_DEBUG_VIEW = "debug.com.reteno.debug.view"
const val PROP_VALUE_DEBUG_VIEW_ENABLE = "enable"
const val THREAD_PREFIX_NAME = "Reteno_thread_"