package com.joseleandro.scaniqo.ui.components

import com.joseleandro.scaniqo.core.designsystem.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.joseleandro.scaniqo.domain.model.TagColor
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel

/** Android presentation of the persisted, platform-independent tag values. */
val TagModel.color: Color get() = Color(colorArgb)
val TagColor.color: Color get() = Color(argb)

@get:StringRes
val TagColor.labelRes: Int
    get() = when (this) {
        TagColor.BLUE -> R.string.tag_color_blue
        TagColor.SKY -> R.string.tag_color_sky
        TagColor.CYAN -> R.string.tag_color_cyan
        TagColor.TEAL -> R.string.tag_color_teal
        TagColor.EMERALD -> R.string.tag_color_emerald
        TagColor.GREEN -> R.string.tag_color_green
        TagColor.LIME -> R.string.tag_color_lime
        TagColor.OLIVE -> R.string.tag_color_olive
        TagColor.YELLOW -> R.string.tag_color_yellow
        TagColor.AMBER -> R.string.tag_color_amber
        TagColor.ORANGE -> R.string.tag_color_orange
        TagColor.CORAL -> R.string.tag_color_coral
        TagColor.RED -> R.string.tag_color_red
        TagColor.ROSE -> R.string.tag_color_rose
        TagColor.PINK -> R.string.tag_color_pink
        TagColor.MAGENTA -> R.string.tag_color_magenta
        TagColor.PURPLE -> R.string.tag_color_purple
        TagColor.VIOLET -> R.string.tag_color_violet
        TagColor.INDIGO -> R.string.tag_color_indigo
        TagColor.NAVY -> R.string.tag_color_navy
        TagColor.BROWN -> R.string.tag_color_brown
        TagColor.TERRACOTTA -> R.string.tag_color_terracotta
        TagColor.SLATE -> R.string.tag_color_slate
        TagColor.GRAPHITE -> R.string.tag_color_graphite
    }

@get:DrawableRes
val TagIcon.drawableRes: Int
    get() = when (this) {
        TagIcon.FOLDER -> R.drawable.ic_tag_folder
        TagIcon.DOCUMENT -> CoreResourcesR.drawable.ic_tag_document
        TagIcon.RECEIPT -> CoreResourcesR.drawable.ic_tag_receipt
        TagIcon.WORK -> CoreResourcesR.drawable.ic_tag_work
        TagIcon.HOME -> R.drawable.ic_tag_home
        TagIcon.HEALTH -> R.drawable.ic_tag_health
        TagIcon.STUDY -> CoreResourcesR.drawable.ic_tag_study
        TagIcon.MONEY -> R.drawable.ic_tag_money
        TagIcon.CARD -> R.drawable.ic_tag_card
        TagIcon.BANK -> R.drawable.ic_tag_bank
        TagIcon.SHOPPING -> R.drawable.ic_tag_shopping
        TagIcon.CAR -> R.drawable.ic_tag_car
        TagIcon.TRAVEL -> R.drawable.ic_tag_travel
        TagIcon.FOOD -> R.drawable.ic_tag_food
        TagIcon.GIFT -> CoreResourcesR.drawable.ic_tag_gift
        TagIcon.HEART -> CoreResourcesR.drawable.ic_tag_heart
        TagIcon.STAR -> CoreResourcesR.drawable.ic_tag_star
        TagIcon.SHIELD -> R.drawable.ic_tag_shield
        TagIcon.KEY -> R.drawable.ic_tag_key
        TagIcon.PHOTO -> CoreResourcesR.drawable.ic_tag_photo
        TagIcon.CALENDAR -> R.drawable.ic_tag_calendar
        TagIcon.TOOLS -> CoreResourcesR.drawable.ic_tag_tools
        TagIcon.PERSON -> CoreResourcesR.drawable.ic_tag_person
        TagIcon.LABEL -> CoreResourcesR.drawable.ic_tag_label
    }

@get:StringRes
val TagIcon.labelRes: Int
    get() = when (this) {
        TagIcon.FOLDER -> R.string.tag_icon_folder
        TagIcon.DOCUMENT -> R.string.tag_icon_document
        TagIcon.RECEIPT -> CoreResourcesR.string.tag_icon_receipt
        TagIcon.WORK -> R.string.tag_icon_work
        TagIcon.HOME -> R.string.tag_icon_home
        TagIcon.HEALTH -> R.string.tag_icon_health
        TagIcon.STUDY -> R.string.tag_icon_study
        TagIcon.MONEY -> R.string.tag_icon_money
        TagIcon.CARD -> R.string.tag_icon_card
        TagIcon.BANK -> R.string.tag_icon_bank
        TagIcon.SHOPPING -> R.string.tag_icon_shopping
        TagIcon.CAR -> R.string.tag_icon_car
        TagIcon.TRAVEL -> R.string.tag_icon_travel
        TagIcon.FOOD -> R.string.tag_icon_food
        TagIcon.GIFT -> R.string.tag_icon_gift
        TagIcon.HEART -> R.string.tag_icon_heart
        TagIcon.STAR -> R.string.tag_icon_star
        TagIcon.SHIELD -> R.string.tag_icon_shield
        TagIcon.KEY -> R.string.tag_icon_key
        TagIcon.PHOTO -> R.string.tag_icon_photo
        TagIcon.CALENDAR -> R.string.tag_icon_calendar
        TagIcon.TOOLS -> R.string.tag_icon_tools
        TagIcon.PERSON -> R.string.tag_icon_person
        TagIcon.LABEL -> R.string.tag_icon_label
    }

fun checkColor(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White
