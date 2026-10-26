package com.sprint.mission.upload.domain;

/** 업로드 용도다. 객체 키의 첫 경로가 된다. */
public enum UploadPurpose {
    PRODUCT(true), BANNER(true), REVIEW(false);

    private final boolean adminOnly;

    UploadPurpose(boolean adminOnly) {
        this.adminOnly = adminOnly;
    }

    public boolean adminOnly() {
        return adminOnly;
    }
}
