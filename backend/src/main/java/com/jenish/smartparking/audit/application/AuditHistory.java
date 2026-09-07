package com.jenish.smartparking.audit.application;

import com.jenish.smartparking.audit.domain.AuditEvent;
import com.jenish.smartparking.facility.domain.FacilityId;
import java.time.Instant;
import java.util.List;

public interface AuditHistory {

    List<AuditEvent> find(FacilityId facilityId, Instant beforeExclusive, int limit);
}
