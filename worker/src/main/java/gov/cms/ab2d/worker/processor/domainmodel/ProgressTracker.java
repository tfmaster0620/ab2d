package gov.cms.ab2d.worker.processor.domainmodel;

import gov.cms.ab2d.worker.adapter.bluebutton.GetPatientsByContractResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
public class ProgressTracker {

    private final String jobUuid;

    @Singular
    private final List<GetPatientsByContractResponse> patientsByContracts;
    private int totalCount;
    private int processedCount;

    private final int failureThreshold;
    private int failureCount;

    @Setter
    private int lastDbUpdateCount;

    @Setter
    private int lastLogUpdateCount;

    @Setter
    private int lastUpdatedPercentage;

    @Setter
    private int optOutCount;

    /**
     * Increment the number of patients processed
     */
    public void incrementProcessedCount() {
        ++processedCount;
    }

    public void incrementFailureCount() {
        ++failureCount;
    }

    public void incrementOptOutCount() {
        ++optOutCount;
    }

    public int getContractCount(String contractNumber) {
        GetPatientsByContractResponse response = patientsByContracts.stream()
                .filter(c -> contractNumber.equalsIgnoreCase(c.getContractNumber()))
                .findFirst().orElse(null);
        if (response == null || response.getPatients() == null) {
            return 0;
        }
        return response.getPatients().size();
    }
    /**
     * Get the total number of patients we're processing across all contracts
     *
     * @return number of patients
     */
    public int getTotalCount() {
        if (totalCount == 0) {
            totalCount = patientsByContracts.stream()
                    .mapToInt(patientsByContract -> patientsByContract.getPatients().size())
                    .sum();
        }

        return totalCount;
    }

    /**
     * If it's been a long time (by frequency of processed patients) since we've updated the DB
     *
     * @param reportProgressFrequency - how many patients between updates
     * @return true if it's been long enough
     */
    public boolean isTimeToUpdateDatabase(int reportProgressFrequency) {
        return processedCount - lastDbUpdateCount >= reportProgressFrequency;
    }

    /**
     * If it's been a long time (by frequency of processed patients) since we've updated the log
     *
     * @param reportProgressLogFrequency - how many patients between updates
     * @return true if it's  been long enough
     */
    public boolean isTimeToLog(int reportProgressLogFrequency) {
        return processedCount - lastLogUpdateCount >= reportProgressLogFrequency;
    }

    /**
     * Return the percentage complete on the job by dividing the processed count by the total count of
     * patients and multiplying by 100 as an integer (0-100)
     *
     * @return the percent complete
     */
    public int getPercentageCompleted() {
        final int percentCompleted = (processedCount * 100) / getTotalCount();
        lastDbUpdateCount = processedCount;
        return percentCompleted;
    }

    public boolean isErrorCountBelowThreshold() {
        return (failureCount * 100) / getTotalCount() < failureThreshold;
    }
}