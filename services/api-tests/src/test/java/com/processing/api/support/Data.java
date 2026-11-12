package com.processing.api.support;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class Data {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger STAN = new AtomicInteger(RANDOM.nextInt(1_000_000));

    private Data() { }

    public static String digits(int length) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < length; index++) {
            value.append(RANDOM.nextInt(10));
        }
        return value.toString();
    }

    public static Map<String, Object> card() {
        return new LinkedHashMap<>(Map.of("bin", "400000", "cardholderName", "MARIA GERILOVICH",
                "currencyCode", "643", "dailyLimit", 10000, "monthlyLimit", 100000, "initialBalance", 1000000));
    }

    public static Map<String, Object> authorization(String pan) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mti", "0100");
        body.put("stan", String.format("%06d", Math.floorMod(STAN.getAndIncrement(), 1_000_000)));
        body.put("pan", pan);
        body.put("processingCode", "000000");
        body.put("amount", new BigDecimal("100"));
        body.put("currencyCode", "643");
        body.put("transmissionDateTime", Instant.now().toString());
        body.put("terminalId", "T" + digits(7));
        body.put("terminalType", "POS");
        body.put("merchantId", "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        body.put("mcc", "5411");
        body.put("acquirerId", "ACQ001");
        body.put("issuerId", "ISS001");
        return body;
    }

    public static Map<String, Object> transaction() {
        Map<String, Object> body = authorization("400000" + digits(10));
        String timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS).toString();
        body.put("transmissionDateTime", timestamp);
        body.put("id", UUID.randomUUID().toString());
        body.put("rrn", digits(12));
        body.put("status", "APPROVED");
        body.put("declineReason", null);
        body.put("authCode", "ABC123");
        body.put("processingTimeMs", 1);
        body.put("acquiringFee", 0);
        body.put("createdAt", timestamp);
        return body;
    }
}
