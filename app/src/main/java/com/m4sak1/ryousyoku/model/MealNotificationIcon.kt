package com.m4sak1.ryousyoku.model

import androidx.annotation.DrawableRes
import com.m4sak1.ryousyoku.R

/** Bundled official Material Symbols; no font download or network dependency. */
enum class MealNotificationIcon(val key: String, val label: String, @DrawableRes val drawableRes: Int) {
    FORK_SPOON("fork_spoon", "フォークとスプーン", R.drawable.ic_notification_symbol_fork_spoon),
    KANJI_ALCOHOL("kanji_alcohol", "酒", R.drawable.ic_notification_symbol_kanji_alcohol),
    RESTAURANT("restaurant", "レストラン", R.drawable.ic_notification_symbol_restaurant),
    RICE_BOWL("rice_bowl", "ごはん", R.drawable.ic_notification_symbol_rice_bowl),
    SET_MEAL("set_meal", "定食", R.drawable.ic_notification_symbol_set_meal),
    BENTO("bento", "お弁当", R.drawable.ic_notification_symbol_bento),
    RAMEN_DINING("ramen_dining", "ラーメン", R.drawable.ic_notification_symbol_ramen_dining),
    UDON("udon", "うどん", R.drawable.ic_notification_symbol_udon),
    SOBA("soba", "そば", R.drawable.ic_notification_symbol_soba),
    JAPANESE_CURRY("japanese_curry", "カレー", R.drawable.ic_notification_symbol_japanese_curry),
    WASHOKU("washoku", "和食", R.drawable.ic_notification_symbol_washoku),
    MENU_BOOK("menu_book", "献立表", R.drawable.ic_notification_symbol_menu_book),
    BREAKFAST_DINING("breakfast_dining", "トースト", R.drawable.ic_notification_symbol_breakfast_dining),
    BAKERY_DINING("bakery_dining", "クロワッサン", R.drawable.ic_notification_symbol_bakery_dining),
    LUNCH_DINING("lunch_dining", "ハンバーガー", R.drawable.ic_notification_symbol_lunch_dining),
    LOCAL_PIZZA("local_pizza", "ピザ", R.drawable.ic_notification_symbol_local_pizza),
    FASTFOOD("fastfood", "ファストフード", R.drawable.ic_notification_symbol_fastfood),
    EGG("egg", "たまご", R.drawable.ic_notification_symbol_egg),
    COOKING("cooking", "料理", R.drawable.ic_notification_symbol_cooking),
    DINNER_DINING("dinner_dining", "夕食", R.drawable.ic_notification_symbol_dinner_dining),
    CAKE("cake", "ケーキ", R.drawable.ic_notification_symbol_cake),
    COOKIE("cookie", "クッキー", R.drawable.ic_notification_symbol_cookie),
    ICECREAM("icecream", "アイス", R.drawable.ic_notification_symbol_icecream),
    LOCAL_CAFE("local_cafe", "カフェ", R.drawable.ic_notification_symbol_local_cafe),
    COFFEE("coffee", "コーヒー", R.drawable.ic_notification_symbol_coffee),
    LOCAL_DRINK("local_drink", "飲み物", R.drawable.ic_notification_symbol_local_drink),
    SPORTS_BAR("sports_bar", "ビール", R.drawable.ic_notification_symbol_sports_bar),
    LIQUOR("liquor", "お酒", R.drawable.ic_notification_symbol_liquor);

    val title: String get() = key.split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: FORK_SPOON
    }
}
