package com.project.common.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Sinh UUID version 7 (RFC 9562): 48 bit thời gian Unix (ms) + version + 12 bit bộ đếm + variant + 62 bit ngẫu nhiên.
 * <ul>
 * <li>Tăng dần theo thời gian: index B-tree chèn vào cuối như id tự tăng, không bị phân mảnh như UUID v4.</li>
 * <li>Trong cùng 1 ms vẫn tăng dần (bộ đếm 12 bit), kể cả khi đồng hồ hệ thống lùi lại.</li>
 * <li>Sinh ngay trong ứng dụng, không cần hỏi DB như IDENTITY: biết id trước khi lưu, Hibernate gom được
 * nhiều insert thành 1 batch.</li>
 * </ul>
 * Không phụ thuộc JPA. Cho entity thì dùng {@code @UUIDV7} (com.project.common.jpa).
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MAX_COUNTER = 0xFFF;

    private static long lastMillis = -1;
    private static long counter;

    private UuidV7() {
    }

    public static UUID next() {
        long millis;
        long seq;
        synchronized (UuidV7.class) {
            long now = System.currentTimeMillis();
            if (now > lastMillis) {
                lastMillis = now;
                // Bắt đầu bộ đếm ở giá trị ngẫu nhiên nửa dưới: khó đoán thứ tự, vẫn còn chỗ để tăng
                counter = RANDOM.nextInt(0x800);
            } else if (++counter > MAX_COUNTER) {
                // Hết 4096 id trong 1 ms (hoặc đồng hồ lùi): mượn ms kế tiếp để vẫn tăng dần
                lastMillis++;
                counter = 0;
            }
            millis = lastMillis;
            seq = counter;
        }
        long msb = (millis << 16) | 0x7000L | seq;
        long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    /** Thời điểm (Unix ms) mã hoá trong UUID v7. */
    public static long timestampMillis(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Không phải UUID v7: " + uuid);
        }
        return uuid.getMostSignificantBits() >>> 16;
    }
}
