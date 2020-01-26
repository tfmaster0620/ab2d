package gov.cms.ab2d.worker.processor.domainmodel;

import gov.cms.ab2d.worker.adapter.bluebutton.GetPatientsByContractResponse.PatientDTO;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class JobDM {

    private final String jobUuid;
    private final Long jobId;

    @Singular
    private final List<ContractDM> contracts;


    @Getter
    @Builder
    public static class ContractDM {
        private final Long contractId;
        private final String contractNumber;
        private Map<Integer, List<PatientDTO>> slices;

    }


}