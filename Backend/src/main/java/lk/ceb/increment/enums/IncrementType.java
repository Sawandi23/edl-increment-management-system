package lk.ceb.increment.enums;

public enum IncrementType {

    /** Normal annual step increment (steps 1–15) */
    NORMAL,

    /** Efficiency bar — employee must meet performance threshold */
    EFFICIENCY_BAR,

    /** Post step-15 surplus stage — AP form only in off years, cert in on years */
    SURPLUS,

    /** Manually entered by HR admin — overrides all calculations */
    MANUAL
}
