package com.joseleandro.scaniqo.domain.model

/** As chaves são estáveis para manter os ícones das tags já salvas. */
enum class TagIcon(
    val key: String,
) {
    FOLDER("folder"),
    DOCUMENT("document"),
    RECEIPT("receipt"),
    WORK("work"),
    HOME("home"),
    HEALTH("health"),
    STUDY("study"),
    MONEY("money"),
    CARD("card"),
    BANK("bank"),
    SHOPPING("shopping"),
    CAR("car"),
    TRAVEL("travel"),
    FOOD("food"),
    GIFT("gift"),
    HEART("heart"),
    STAR("star"),
    SHIELD("shield"),
    KEY("key"),
    PHOTO("photo"),
    CALENDAR("calendar"),
    TOOLS("tools"),
    PERSON("person"),
    LABEL("label");

    companion object {
        // Ícones desconhecidos continuam legíveis sem alterar a chave persistida.
        fun fromKey(key: String): TagIcon =
            entries.firstOrNull { it.key == key } ?: FOLDER
    }
}
