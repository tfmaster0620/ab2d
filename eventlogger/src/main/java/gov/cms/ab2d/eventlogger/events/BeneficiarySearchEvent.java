package gov.cms.ab2d.eventlogger.events;

import gov.cms.ab2d.eventlogger.LoggableEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class BeneficiarySearchEvent extends LoggableEvent {
    // The HTTP response code
    private String response;
    // When a response is received
    private OffsetDateTime responseDate;
    // The beneficiary searched on
    private String beneId;
    // The contract it's part of
    private String contractNum;

    public BeneficiarySearchEvent() { }

    public BeneficiarySearchEvent(String user, String jobId, String contractNum,
                                  OffsetDateTime startTime, OffsetDateTime endTime,
                                  String beneId, String response) {
        super(startTime, user, jobId);
        this.response = response;
        this.contractNum = contractNum;
        this.responseDate = endTime;
        this.beneId = beneId;
    }
}
