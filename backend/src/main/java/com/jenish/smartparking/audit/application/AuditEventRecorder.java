package com.jenish.smartparking.audit.application;

import com.jenish.smartparking.audit.domain.AuditEvent;

@FunctionalInterface
public interface AuditEventRecorder {

    void record(AuditEvent event);
}
