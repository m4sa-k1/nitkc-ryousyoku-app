package com.m4sak1.ryousyoku.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.m4sak1.ryousyoku.model.*
import com.m4sak1.ryousyoku.repository.LoadedStructuredMenu
import com.m4sak1.ryousyoku.repository.MenuRepository
import com.m4sak1.ryousyoku.repository.StructuredNotReady
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.LocalDate
import java.time.ZonedDateTime

class NextMealWidgetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (NextMealWidgetController.ids(context).isEmpty()) return Result.success()
        val repository = MenuRepository(context.cacheDir)
        var loaded: LoadedStructuredMenu? = null
        var unavailable: LocalDate? = null
        // A slow request can cross the dinner boundary into tomorrow's menu.
        for (attempt in 0..1) {
            val date = nextMealSlot(ZonedDateTime.now(MenuTimeZone)).date
            unavailable = null
            loaded = null
            try {
                val matches = repository.fetchMenus().filter { menuCoversDate(it, date) }.distinctBy { it.filename }
                if (matches.size == 1) loaded = repository.fetchStructured(matches.single()) else unavailable = date
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: StructuredNotReady) { unavailable = date
            } catch (_: IOException) { loaded = NextMealWidgetController.cached(context, date)
            } catch (_: Exception) { unavailable = date }
            if (unavailable == date) NextMealWidgetController.discardCachedDate(context, date)
            if (nextMealSlot(ZonedDateTime.now(MenuTimeZone)).date == date) break
        }
        NextMealWidgetController.render(context, loaded, unavailable)
        NextMealWidgetController.schedule(context)
        return Result.success()
    }
}
