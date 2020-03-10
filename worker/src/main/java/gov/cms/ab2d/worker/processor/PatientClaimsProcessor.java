package gov.cms.ab2d.worker.processor;

import com.newrelic.api.agent.Token;
import gov.cms.ab2d.worker.adapter.bluebutton.GetPatientsByContractResponse.PatientDTO;

import java.time.OffsetDateTime;
import java.util.concurrent.Future;

public interface PatientClaimsProcessor {
    Future<Void> process(PatientDTO patientDTO, StreamHelper helper, OffsetDateTime attTime,
                         OffsetDateTime sinceTime, Token token);
}
