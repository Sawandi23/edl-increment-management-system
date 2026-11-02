package lk.ceb.increment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ceb.increment.entity.Employee;
import lk.ceb.increment.entity.IncrementCycle;
import lk.ceb.increment.entity.SmsLog;
import lk.ceb.increment.enums.IncrementStatus;
import lk.ceb.increment.enums.SmsStatus;
import lk.ceb.increment.repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DialogSmsService {

    private static final String SMS_URL = "https://richcommunication.dialog.lk/api/sms/send";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private final SmsLogRepository smsLogRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${dialog.sms.enabled:false}")
    private boolean enabled;

    @Value("${dialog.sms.username:}")
    private String username;

    @Value("${dialog.sms.password:}")
    private String password;

    @Value("${dialog.sms.mask:EDL-HR}")
    private String mask;

    /** Sends after a successful workflow transition; SMS failures never block the workflow. */
    public void notifyStatusChange(IncrementCycle cycle, IncrementStatus oldStatus, IncrementStatus newStatus) {
        if (!isNotificationTransition(oldStatus, newStatus)) return;

        Employee employee = cycle.getEmployee();
        String phone = employee == null ? null : employee.getPhoneNumber();
        String message = SmsMessageBuilder.build(newStatus);
        SmsLog smsLog = SmsLog.builder()
                .employee(employee)
                .incrementCycle(cycle)
                .toPhone(phone == null || phone.isBlank() ? "UNKNOWN" : phone)
                .messageText(message)
                .status(SmsStatus.PENDING)
                .build();

        try {
            if (employee == null || phone == null || phone.isBlank()) {
                saveSkipped(smsLog, "Skipped: employee phone number is missing.");
                log.warn("[SMS] Skipped cycle {}: employee phone number is missing.", cycle.getId());
                return;
            }

            if (!enabled) {
                saveSkipped(smsLog, "Skipped: Dialog SMS is disabled by configuration.");
                log.info("[SMS-DISABLED] Cycle {} event {} would notify {}.", cycle.getId(), newStatus, phone);
                return;
            }

            String normalizedPhone = normalizeSri LankanNumber(phone);
            if (normalizedPhone == null) {
                saveSkipped(smsLog, "Skipped: invalid Sri Lankan mobile number format.");
                log.warn("[SMS] Skipped cycle {}: invalid phone number format.", cycle.getId());
                return;
            }

            smsLog.setToPhone(normalizedPhone);
            smsLogRepository.save(smsLog);

            Map<String, Object> messagePayload = Map.of(
                    "number", normalizedPhone,
                    "mask", mask,
                    "text", message,
                    "campaignName", campaignName(newStatus));
            String json = objectMapper.writeValueAsString(Map.of("messages", List.of(messagePayload)));
            String digest = md5(password);
            String created = LocalDateTime.now(COLOMBO).format(TIMESTAMP_FORMAT);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SMS_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("USER", username)
                    .header("DIGEST", digest)
                    .header("CREATED", created)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            smsLog.setProviderResponse("HTTP " + response.statusCode() + ": " + safeResponse(response.body()));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // SENT means accepted by the gateway; it does not confirm handset delivery.
                smsLog.setStatus(SmsStatus.SENT);
                log.info("[SMS] Dialog accepted notification for cycle {} event {} (HTTP {}).",
                        cycle.getId(), newStatus, response.statusCode());
            } else {
                smsLog.setStatus(SmsStatus.FAILED);
                log.error("[SMS] Dialog rejected notification for cycle {} event {} (HTTP {}).",
                        cycle.getId(), newStatus, response.statusCode());
            }
            smsLogRepository.save(smsLog);
        } catch (Exception ex) {
            log.error("[SMS] Failed to process notification for cycle {} event {}: {}",
                    cycle.getId(), newStatus, ex.getMessage());
            try {
                smsLog.setStatus(SmsStatus.FAILED);
                smsLog.setProviderResponse("SMS exception: " + safeResponse(ex.getMessage()));
                smsLogRepository.save(smsLog);
            } catch (Exception logException) {
                log.error("[SMS] Could not persist SMS failure log for cycle {}: {}",
                        cycle.getId(), logException.getMessage());
            }
        }
    }

    private void saveSkipped(SmsLog smsLog, String reason) {
        smsLog.setStatus(SmsStatus.SKIPPED);
        smsLog.setProviderResponse(reason);
        smsLogRepository.save(smsLog);
    }

    private boolean isNotificationTransition(IncrementStatus oldStatus, IncrementStatus newStatus) {
        // Match the four real workflow actions, not every transition into a status.
        return (oldStatus == IncrementStatus.AP_DRAFT && newStatus == IncrementStatus.AP_SENT)
                || (newStatus == IncrementStatus.AP_STOPPED)
                || (oldStatus == IncrementStatus.AP_SENT && newStatus == IncrementStatus.CERTIFICATE_READY)
                || (oldStatus == IncrementStatus.CERTIFICATE_ISSUED && newStatus == IncrementStatus.PAID);
    }

    private String campaignName(IncrementStatus status) {
        return switch (status) {
            case AP_SENT -> "EDL Appraisal Report Generated";
            case AP_STOPPED -> "EDL Increment Stopped";
            case CERTIFICATE_READY -> "EDL Unit Approved";
            case PAID -> "EDL Increment Certificate Generated";
            default -> "EDL Increment Update";
        };
    }

    private String normalizeSri LankanNumber(String number) {
        if (number == null) return null;
        String cleaned = number.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+94")) {
            cleaned = cleaned.substring(1);
        } else if (cleaned.startsWith("0")) {
            cleaned = "94" + cleaned.substring(1);
        }
        return cleaned.matches("94\\d{9}") ? cleaned : null;
    }

    private String md5(String input) throws Exception {
        if (input == null || input.isBlank()) {
            throw new IllegalStateException("Dialog SMS password is not configured.");
        }
        byte[] hash = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format("%02x", value));
        return result.toString();
    }

    private String safeResponse(String value) {
        if (value == null) return "";
        return value.length() <= 1500 ? value : value.substring(0, 1500);
    }
}
