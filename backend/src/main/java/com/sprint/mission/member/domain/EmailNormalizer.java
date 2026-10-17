package com.sprint.mission.member.domain;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }
}
