package com.wangzan101.mika.ledger

import android.content.Context
import com.wangzan101.mika.core.ledger.LightEntry
import com.wangzan101.mika.core.ledger.LightLedger
import java.io.File

object LightLedgers {
    @Volatile private var instance: LightLedger? = null

    @Synchronized fun get(context: Context): LightLedger {
        instance?.let { return it }
        return LightLedger(File(context.applicationContext.filesDir, "light-ledger.json")).also { instance = it }
    }

    fun record(context: Context, entry: LightEntry) = get(context).record(entry)
}
