package gov.cms.ab2d.common.service;

import gov.cms.ab2d.common.model.JobOutput;
import gov.cms.ab2d.common.model.Role;
import gov.cms.ab2d.common.model.User;
import gov.cms.ab2d.common.repository.JobRepository;

import gov.cms.ab2d.common.model.Job;
import gov.cms.ab2d.common.model.JobStatus;
import gov.cms.ab2d.common.model.Sponsor;
import gov.cms.ab2d.common.model.Contract;
import gov.cms.ab2d.common.repository.ContractRepository;
import gov.cms.ab2d.eventlogger.LogManager;
import gov.cms.ab2d.eventlogger.events.FileEvent;
import gov.cms.ab2d.eventlogger.events.JobStatusChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static gov.cms.ab2d.common.util.Constants.ADMIN_ROLE;

@Slf4j
@Service
@Transactional
public class JobServiceImpl implements JobService {

    private final UserService userService;
    private final JobRepository jobRepository;
    private final ContractRepository contractRepository;
    private final JobOutputService jobOutputService;
    private final LogManager eventLogger;

    @Value("${efs.mount}")
    private String fileDownloadPath;

    public static final String INITIAL_JOB_STATUS_MESSAGE = "0%";

    public JobServiceImpl(UserService userService, JobRepository jobRepository, ContractRepository contractRepository, JobOutputService jobOutputService, LogManager eventLogger) {
        this.userService = userService;
        this.jobRepository = jobRepository;
        this.contractRepository = contractRepository;
        this.jobOutputService = jobOutputService;
        this.eventLogger = eventLogger;
    }

    @Override
    public Job createJob(String resourceTypes, String url, String outputFormat) {
        return createJob(resourceTypes, url, null, outputFormat, null);
    }

    @Override
    public Job createJob(String resourceTypes, String url, String contractNumber, String outputFormat, OffsetDateTime since) {
        Job job = new Job();
        job.setResourceTypes(resourceTypes);
        job.setJobUuid(UUID.randomUUID().toString());
        job.setRequestUrl(url);
        job.setStatus(JobStatus.SUBMITTED);
        job.setStatusMessage(INITIAL_JOB_STATUS_MESSAGE);
        job.setCreatedAt(OffsetDateTime.now());
        job.setOutputFormat(outputFormat);
        job.setProgress(0);
        job.setSince(since);
        job.setUser(userService.getCurrentUser());

        if (contractNumber != null) {
            contractRepository.findContractByContractNumber(contractNumber).ifPresentOrElse(contractFound -> {
                User user = userService.getCurrentUser();
                Sponsor userSponsor = user.getSponsor();

                List<Contract> contracts = userSponsor.getAggregatedAttestedContracts();
                if (!contracts.contains(contractFound)) {
                    log.error("No attested contract with contract number {} found for the user", contractFound.getContractNumber());
                    throw new InvalidContractException("No attested contract with contract number " + contractFound.getContractNumber() +
                        " found for the user");
                }

                job.setContract(contractFound);
            }, () -> {
                log.error("Contract {} was not found", contractNumber);
                throw new ContractNotFoundException("Contract " + contractNumber + " was not found");
            });
        }
        eventLogger.log(new JobStatusChangeEvent(
                job.getUser() == null ? null : job.getUser().getUsername(),
                job.getJobUuid(), null, JobStatus.SUBMITTED.name(), "Job Created"));

        return jobRepository.save(job);
    }

    @Override
    public void cancelJob(String jobUuid) {
        Job job = getAuthorizedJobByJobUuid(jobUuid);

        if (!job.getStatus().isCancellable()) {
            log.error("Job had a status of {} so it was not able to be cancelled", job.getStatus());
            throw new InvalidJobStateTransition("Job has a status of " + job.getStatus() + ", so it cannot be cancelled");
        }
        eventLogger.log(new JobStatusChangeEvent(
                job.getUser() == null ? null : job.getUser().getUsername(),
                job.getJobUuid(),
                job.getStatus() == null ? null : job.getStatus().name(),
                JobStatus.CANCELLED.name(), "Job Cancelled"));

        jobRepository.cancelJobByJobUuid(jobUuid);
    }

    private Job getAuthorizedJobByJobUuid(String jobUuid) {
        Job job = getJobByJobUuid(jobUuid);

        User user = userService.getCurrentUser();
        if (!user.equals(job.getUser())) {
            log.error("User attempted to download a file where they had a valid UUID, but was not logged in as the " +
                    "user that created the job");
            throw new InvalidJobAccessException("Unauthorized");
        }

        return job;
    }

    @Override
    public Job getAuthorizedJobByJobUuidAndRole(String jobUuid) {
        User user = userService.getCurrentUser();

        for (Role role : user.getRoles()) {
            if (role.getName().equals(ADMIN_ROLE)) {
                log.info("Admin accessed job {}", jobUuid);
                return getJobByJobUuid(jobUuid);
            }
        }

        return getAuthorizedJobByJobUuid(jobUuid);
    }

    @Override
    public Job getJobByJobUuid(String jobUuid) {
        Job job = jobRepository.findByJobUuid(jobUuid);

        if (job == null) {
            log.error("Job was searched for and was not found");
            throw new ResourceNotFoundException("No job with jobUuid " +  jobUuid + " was found");
        }

        return job;
    }

    @Override
    public Job updateJob(Job job) {
        return jobRepository.save(job);
    }

    @Override
    public Resource getResourceForJob(String jobUuid, String fileName) throws MalformedURLException {
        Job job = getAuthorizedJobByJobUuid(jobUuid);

        // Make sure that there is a path that matches a job output for the job they are requesting
        boolean jobOutputMatchesPath = false;
        JobOutput foundJobOutput = null;
        for (JobOutput jobOutput : job.getJobOutputs()) {
            if (jobOutput.getFilePath().equals(fileName)) {
                jobOutputMatchesPath = true;
                foundJobOutput = jobOutput;
                break;
            }
        }

        if (!jobOutputMatchesPath) {
            log.error("No Job Output with the file name {} exists in our records", fileName);
            throw new ResourceNotFoundException("No Job Output with the file name " + fileName + " exists in our records");
        }

        Path file = Paths.get(fileDownloadPath, job.getJobUuid(), fileName);
        Resource resource = new UrlResource(file.toUri());

        if (!resource.exists()) {
            String errorMsg;
            if (foundJobOutput.getDownloaded()) {
                errorMsg = "The file is not present as it has already been downloaded. Please resubmit the job.";
            } else if (job.getExpiresAt().isBefore(OffsetDateTime.now())) {
                errorMsg = "The file is not present as it has expired. Please resubmit the job.";
            } else {
                errorMsg = "The file is not present as there was an error. Please resubmit the job.";
            }
            log.error(errorMsg);
            throw new JobOutputMissingException(errorMsg);
        }

        return resource;
    }

    @Override
    public void deleteFileForJob(File file, String jobUuid) {
        String fileName = file.getName();
        Job job = jobRepository.findByJobUuid(jobUuid);
        JobOutput jobOutput = jobOutputService.findByFilePathAndJob(fileName, job);
        jobOutput.setDownloaded(true);
        jobOutputService.updateJobOutput(jobOutput);
        eventLogger.log(new FileEvent(
                job == null || job.getUser() == null ? null : job.getUser().getUsername(),
                jobUuid, file, FileEvent.FileStatus.DELETE));
        boolean deleted = file.delete();
        if (!deleted) {
            log.error("Was not able to delete the file {}", file.getName());
        }
    }

    @Override
    public boolean checkIfCurrentUserCanAddJob() {
        User user = userService.getCurrentUser();
        List<Job> jobs = jobRepository.findActiveJobsByUser(user);
        return jobs.size() < user.getMaxParallelJobs();
    }
}
