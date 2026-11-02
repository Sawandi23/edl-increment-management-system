package lk.ceb.increment.service;

import lk.ceb.increment.enums.IncrementStatus;

/**
 * Dummy multilingual SMS templates for the EDL Increment Management System.
 * Replace the placeholder text with the approved Sinhala, English, and Tamil
 * wording. All three languages are sent together in ONE SMS body.
 */
public final class SmsMessageBuilder {

    private SmsMessageBuilder() {
    }

    public static String build(IncrementStatus event) {
        return switch (event) {
            case AP_SENT -> appraisalReportGenerated();
            case AP_STOPPED -> incrementStopped();
            case CERTIFICATE_READY -> unitApproved();
            case PAID -> incrementCertificateGenerated();
            default -> "EDL SMS test message: workflow status updated.";
        };
    }

    /** Trigger: Generate Appraisal Report */
    private static String appraisalReportGenerated() {
        return """
                 ඔබගේ වැටුප් වැඩීවීම සඳහා වු වාර්ෂික කාර්යය සාධන ඇගයීම් පත්‍රය ඔබගේ ඒකක ප්‍රධානියා වෙත යොමුකර ඇත. 
                 Your Annual Performance Appraisal report has been forwarded to your Unit Head.
                 உங்கள் வருடாந்த செயல்திறன் மதிப்பீட்டு அறிக்கை உங்கள் பிரிவுத் தலைவருக்கு அனுப்பப்பட்டுள்ளது""".strip();
    }

    /** Trigger: Mark as Stopped */
    private static String incrementStopped() {
        return """
                ඔබගේ වැටුප් වැඩිවීම අනුමත වී නොමැති අතර වැඩි විස්තර සඳහා මා.ස.නි (කො.න) 0717008219 යන අංකයට අමතන්න. 
                Your salary increment has not been approved. For further details, please contact HRO (Colombo City) on 0717008219.
                உங்கள் சம்பள உயர்வு அங்கீகரிக்கப்படவில்லை. மேலதிக விபரங்களுக்கு மா.ச.நி. (கொ.ந) அவர்களை 0717008219 என்ற தொலைபேசி இலக்கத்தில் தொடர்புகொள்ளவும்""".strip();
    }

    /** Trigger: Unit Approved — Issue Certificate */
    private static String unitApproved() {
        return """
                ඔබගේ වැටුප් වැඩිවීම අනුමත වී ඇති අතර, ගෙවීම කිරීම සඳහා කටයුතු කරනු ලැබේ.
                Your appraisal report has been approved. & Arrangements are being made for payment.
                உங்கள் சம்பள உயர்வு அங்கீகரிக்கப்பட்டுள்ளது. அதற்கான கொடுப்பனவை வழங்குவதற்கான நடவடிக்கைகள் மேற்கொள்ளப்பட்டு வருகின்றன.""".strip();
    }

    /** Trigger: Download Increment Certificate */
    private static String incrementCertificateGenerated() {
        return """
                ඔබගේ වැටුප් වැඩිවීමේ සහතිකය සකස් කර ඇති අතර,එය ගෙවීම් කටයුතු සඳහා ගණකාධිකාරි (වියදම්) කො.න වෙත යොමුකර ඇත.
                Your salary increment certificate has been prepared and forwarded to the Accountant (Expenditure), Col.City, for payment processing.
                உங்கள் சம்பள உயர்வுச் சான்றிதழ் தயாரிக்கப்பட்டு, கொடுப்பனவு நடவடிக்கைகளுக்காக கணக்காளர் (செலவுகள்), கொ.ந. அவர்களுக்கு அனுப்பப்பட்டுள்ளது.""".strip();
    }
}
