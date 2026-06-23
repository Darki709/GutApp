package com.example.gutapp.session.Responses;

import com.example.gutapp.database.ChartStateDao;
import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Responses for the chart-state sync API (drawings + indicators + presets).
 *
 * Wire layout (after the 1B msgType + 4B reqId header stripped by the framing):
 *  PushResult (MsgType 19): [1B status]
 *  PullResult (MsgType 18): [1B status][2B count] then `count` rows of
 *      [1B kindCode][2B keyLen][key][1B deleted][8B updated_at][4B payloadLen][payload]
 *  kindCode: 0 = drawings, 1 = indicators, 2 = preset.
 *
 * All multi-byte integers are big-endian (network order) — matches ByteBuffer's default.
 */
public class ChartSyncResponses {

    /** Result of a PUSH (upload of local dirty rows). */
    public static class PushResult extends AsyncResponse {
        public final byte status;
        public PushResult(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            this.status = response[5];
        }
    }

    /** Result of a PULL — the authoritative rows for the user. */
    public static class PullResult extends AsyncResponse {
        public final byte status;
        public final List<ChartStateDao.Row> rows = new ArrayList<>();

        public PullResult(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length - 5);
            this.status = buffer.get();
            if (this.status != 0) return;

            int count = buffer.getShort() & 0xFFFF;
            for (int i = 0; i < count; i++) {
                int kindCode    = buffer.get() & 0xFF;
                int keyLen      = buffer.getShort() & 0xFFFF;
                byte[] keyBytes = new byte[keyLen];
                buffer.get(keyBytes);
                boolean deleted = (buffer.get() & 0xFF) == 1;
                long updatedAt  = buffer.getLong();
                int payLen      = buffer.getInt();
                byte[] payBytes = new byte[payLen];
                buffer.get(payBytes);

                rows.add(new ChartStateDao.Row(
                        kindFromCode(kindCode),
                        new String(keyBytes, StandardCharsets.UTF_8),
                        new String(payBytes, StandardCharsets.UTF_8),
                        updatedAt, deleted));
            }
        }

        private static String kindFromCode(int code) {
            switch (code) {
                case 1:  return ChartStateDao.KIND_INDICATORS;
                case 2:  return ChartStateDao.KIND_PRESET;
                default: return ChartStateDao.KIND_DRAWINGS;
            }
        }
    }
}
