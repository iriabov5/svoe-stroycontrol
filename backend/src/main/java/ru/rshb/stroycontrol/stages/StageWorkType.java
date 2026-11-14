package ru.rshb.stroycontrol.stages;

/** Вид строительных работ, используемый в плане и будущих отчётах. */
public enum StageWorkType {
    /** Основание дома. */
    FOUNDATION,
    /** Возведение стен. */
    WALLS,
    /** Устройство крыши. */
    ROOF,
    /** Инженерные сети. */
    UTILITIES,
    /** Отделочные работы. */
    FINISHING,
    /** Работы вне основных видов. */
    OTHER
}
