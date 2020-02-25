package gov.cms.ab2d.worker.processor;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.ab2d.bfd.client.BFDClient;
import gov.cms.ab2d.common.util.FHIRUtil;
import gov.cms.ab2d.filter.ExplanationOfBenefitTrimmer;
import gov.cms.ab2d.filter.FilterOutByDate;
import gov.cms.ab2d.worker.adapter.bluebutton.GetPatientsByContractResponse.PatientDTO;
import gov.cms.ab2d.worker.config.RoundRobinThreadPoolTaskExecutor;
import gov.cms.ab2d.worker.config.WorkerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static gov.cms.ab2d.filter.EOBLoadUtilities.isPartD;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatientClaimsProcessorImpl implements PatientClaimsProcessor {

    private final BFDClient bfdClient;
    private final FhirContext fhirContext;
    private final WorkerConfig workerConfig;

    /**
     * Process the retrieval of patient explanation of benefit objects and write them
     * to a file using the writer
     */
    public Future<Void> process(String contractId, PatientDTO patientDTO, final StreamHelper helper, OffsetDateTime attTime) {
        RoundRobinThreadPoolTaskExecutor taskExecutor = (RoundRobinThreadPoolTaskExecutor) workerConfig.patientProcessorThreadPool();
        return (taskExecutor.submitWithCategory(contractId, () -> doTheWork(patientDTO, helper, attTime)));
    }

    private Callable doTheWork(PatientDTO patientDTO, StreamHelper helper, OffsetDateTime attTime) throws Exception {
        int resourceCount = 0;

        String payload = "";
        try {
            // Retrieve the resource bundle of EOB objects
            var resources = getEobBundleResources(patientDTO, attTime);

            var jsonParser = fhirContext.newJsonParser();

            for (var resource : resources) {
                ++resourceCount;
                try {
                    payload = jsonParser.encodeResourceToString(resource) + System.lineSeparator();
                    helper.addData(payload.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    log.warn("Encountered exception while processing job resources: {}", e.getMessage());
                    handleException(helper, payload, e);
                }
            }
        } catch (Exception e) {
            try {
                handleException(helper, payload, e);
            } catch (IOException e1) {
                //should not happen - original exception will be thrown
                log.error("error during exception handling to write error record");
            }
            throw e;
        }

        log.debug("finished writing [{}] resources", resourceCount);

        return null;
    }

    private void handleException(StreamHelper helper, String data, Exception e) throws IOException {
        var errMsg = ExceptionUtils.getRootCauseMessage(e);
        var operationOutcome = FHIRUtil.getErrorOutcome(errMsg);

        var jsonParser = fhirContext.newJsonParser();
        var payload = jsonParser.encodeResourceToString(operationOutcome) + System.lineSeparator();

        var byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        helper.addError(data);
    }

    private List<Resource> getEobBundleResources(PatientDTO patient, OffsetDateTime attTime) {

        Bundle eobBundle = bfdClient.requestEOBFromServer(patient.getPatientId());

        final List<BundleEntryComponent> entries = eobBundle.getEntry();
        final List<Resource> resources = extractResources(entries, patient.getDateRangesUnderContract(), attTime);

        while (eobBundle.getLink(Bundle.LINK_NEXT) != null) {
            eobBundle = bfdClient.requestNextBundleFromServer(eobBundle);
            final List<BundleEntryComponent> nextEntries = eobBundle.getEntry();
            resources.addAll(extractResources(nextEntries, patient.getDateRangesUnderContract(), attTime));
        }

        log.debug("Bundle - Total: {} - Entries: {} ", eobBundle.getTotal(), entries.size());
        return resources;
    }

    private List<Resource> extractResources(List<BundleEntryComponent> entries, final List<FilterOutByDate.DateRange> dateRanges,
                                            OffsetDateTime attTime) {
        if (attTime == null) {
            return new ArrayList<>();
        }
        long epochMilli = attTime.toInstant().toEpochMilli();
        Date attDate = new Date(epochMilli);
        return entries.stream()
                // Get the resource
                .map(BundleEntryComponent::getResource)
                // Get only the explanation of benefits
                .filter(resource -> resource.getResourceType() == ResourceType.ExplanationOfBenefit)
                // Filter by date
                .filter(resource -> FilterOutByDate.valid((ExplanationOfBenefit) resource, attDate, dateRanges))
                // filter it
                .map(resource -> ExplanationOfBenefitTrimmer.getBenefit((ExplanationOfBenefit) resource))
                // Remove any empty values
                .filter(Objects::nonNull)
                // Remove Plan D
                .filter(resource -> !isPartD(resource))
                // compile the list
                .collect(Collectors.toList());
    }
}
