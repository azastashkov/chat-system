package com.chat.common.util;

import com.datastax.oss.driver.api.core.uuid.Uuids;

import java.util.UUID;

public final class TimeUuidUtil {

    private TimeUuidUtil() {}

    public static UUID now() {
        return Uuids.timeBased();
    }

    public static long toUnixTimestamp(UUID timeUuid) {
        return Uuids.unixTimestamp(timeUuid);
    }
}
