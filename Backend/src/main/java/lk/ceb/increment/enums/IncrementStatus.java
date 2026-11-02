package lk.ceb.increment.enums;

/**
 * Full lifecycle of a single increment cycle for one employee.
 *
 * Flow:
 *   ELIGIBLE → AP_DRAFT → AP_SENT → (AP_STOPPED | AP_PAUSED | CERTIFICATE_READY)
 *   CERTIFICATE_READY → CERTIFICATE_ISSUED → PAID
 *   REJECTED — terminal state, HR handles manually outside system
 */
public enum IncrementStatus {

    /** Calculated as due — no action taken yet */
    ELIGIBLE,

    /** AP form has been generated (docx/pdf ready to download) */
    AP_DRAFT,

    /** AP form sent to unit + SMS dispatched */
    AP_SENT,

    /** Unit has stopped/blocked this increment */
    AP_STOPPED,

    /** Increment is paused temporarily (e.g. pending discipline outcome) */
    AP_PAUSED,

    /** Unit accepted — certificate is ready to generate */
    CERTIFICATE_READY,

    /** Certificate generated and downloaded */
    CERTIFICATE_ISSUED,

    /** Payment confirmed + SMS sent to employee */
    PAID,

    /** Rejected by unit — manual follow-up required */
    REJECTED
}
