package com.joseleandro.scaniqo.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.joseleandro.scaniqo.R

/** As chaves são estáveis para manter os ícones das tags já salvas. */
enum class TagIcon(
    val key: String,
    @get:DrawableRes val drawableRes: Int,
    @get:StringRes val labelRes: Int,
) {
    FOLDER("folder", R.drawable.ic_tag_folder, R.string.tag_icon_folder),
    DOCUMENT("document", R.drawable.ic_tag_document, R.string.tag_icon_document),
    RECEIPT("receipt", R.drawable.ic_tag_receipt, R.string.tag_icon_receipt),
    WORK("work", R.drawable.ic_tag_work, R.string.tag_icon_work),
    HOME("home", R.drawable.ic_tag_home, R.string.tag_icon_home),
    HEALTH("health", R.drawable.ic_tag_health, R.string.tag_icon_health),
    STUDY("study", R.drawable.ic_tag_study, R.string.tag_icon_study),
    MONEY("money", R.drawable.ic_tag_money, R.string.tag_icon_money),
    CARD("card", R.drawable.ic_tag_card, R.string.tag_icon_card),
    BANK("bank", R.drawable.ic_tag_bank, R.string.tag_icon_bank),
    SHOPPING("shopping", R.drawable.ic_tag_shopping, R.string.tag_icon_shopping),
    CAR("car", R.drawable.ic_tag_car, R.string.tag_icon_car),
    TRAVEL("travel", R.drawable.ic_tag_travel, R.string.tag_icon_travel),
    FOOD("food", R.drawable.ic_tag_food, R.string.tag_icon_food),
    GIFT("gift", R.drawable.ic_tag_gift, R.string.tag_icon_gift),
    HEART("heart", R.drawable.ic_tag_heart, R.string.tag_icon_heart),
    STAR("star", R.drawable.ic_tag_star, R.string.tag_icon_star),
    SHIELD("shield", R.drawable.ic_tag_shield, R.string.tag_icon_shield),
    KEY("key", R.drawable.ic_tag_key, R.string.tag_icon_key),
    PHOTO("photo", R.drawable.ic_tag_photo, R.string.tag_icon_photo),
    CALENDAR("calendar", R.drawable.ic_tag_calendar, R.string.tag_icon_calendar),
    TOOLS("tools", R.drawable.ic_tag_tools, R.string.tag_icon_tools),
    PERSON("person", R.drawable.ic_tag_person, R.string.tag_icon_person),
    LABEL("label", R.drawable.ic_tag_label, R.string.tag_icon_label);

    companion object {
        // Ícones desconhecidos continuam legíveis sem alterar a chave persistida.
        fun fromKey(key: String): TagIcon =
            entries.firstOrNull { it.key == key } ?: FOLDER
    }
}
