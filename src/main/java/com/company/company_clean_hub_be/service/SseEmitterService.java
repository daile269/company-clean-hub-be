package com.company.company_clean_hub_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý SSE connections của từng user.
 * Khi có notification mới, gọi sendNotification(userId, data) để đẩy real-time.
 */
@Service
@Slf4j
public class SseEmitterService {

    // Key: userId, Value: SseEmitter của user đó
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * User kết nối SSE — gọi khi FE mở kết nối.
     */
    public SseEmitter subscribe(Long userId) {
        // Đóng kết nối cũ nếu user này từng kết nối trước đó
        SseEmitter oldEmitter = emitters.remove(userId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception ignored) {
            }
        }

        // Timeout = 0L (không bao giờ timeout từ phía Spring MVC, giữ kết nối bằng heartbeat @Scheduled)
        SseEmitter emitter = new SseEmitter(0L);

        emitters.put(userId, emitter);
        log.info("SSE subscribed: userId={}", userId);

        emitter.onCompletion(() -> {
            emitters.remove(userId, emitter);
            log.info("SSE completed: userId={}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId, emitter);
            log.info("SSE timeout: userId={}", userId);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(e -> {
            emitters.remove(userId, emitter);
            log.warn("SSE error userId={}: {}", userId, e.getMessage());
        });

        // Gửi event ping đầu tiên để xác nhận kết nối thành công
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(userId, emitter);
        }

        return emitter;
    }

    /**
     * Gửi notification real-time đến 1 user cụ thể.
     * Gọi method này sau khi lưu Notification vào DB.
     */
    public void sendToUser(Long userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            // User không đang mở tab → không cần gửi SSE, FE sẽ fetch khi mở lại
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(data));
            log.info("SSE notification sent to userId={}", userId);
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("SSE send failed for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Đếm số user đang kết nối SSE (debug/monitor).
     */
    public int getConnectedCount() {
        return emitters.size();
    }

    /**
     * Định kỳ 15 giây gửi gói tin heartbeat ping để giữ kết nối TCP luôn thông suốt.
     * Tự động dọn dẹp các kết nối đã ngắt khỏi Memory.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(userId);
                log.debug("Removed dead SSE emitter for userId={}", userId);
            }
        });
    }
}
