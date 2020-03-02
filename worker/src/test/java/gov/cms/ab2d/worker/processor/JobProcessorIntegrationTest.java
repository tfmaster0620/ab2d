package gov.cms.ab2d.worker.processor;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.ab2d.bfd.client.BFDClient;
import gov.cms.ab2d.common.model.Contract;
import gov.cms.ab2d.common.model.Job;
import gov.cms.ab2d.common.model.JobOutput;
import gov.cms.ab2d.common.model.JobStatus;
import gov.cms.ab2d.common.model.Sponsor;
import gov.cms.ab2d.common.model.User;
import gov.cms.ab2d.common.repository.ContractRepository;
import gov.cms.ab2d.common.repository.JobOutputRepository;
import gov.cms.ab2d.common.repository.JobRepository;
import gov.cms.ab2d.common.repository.OptOutRepository;
import gov.cms.ab2d.common.repository.SponsorRepository;
import gov.cms.ab2d.common.repository.UserRepository;
import gov.cms.ab2d.common.util.AB2DPostgresqlContainer;
import gov.cms.ab2d.worker.adapter.bluebutton.ContractAdapter;
import gov.cms.ab2d.worker.service.FileService;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.transaction.Transactional;
import java.io.File;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;

import static gov.cms.ab2d.common.util.Constants.NDJSON_FIRE_CONTENT_TYPE;
import static java.lang.Boolean.TRUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@SpringIntegrationTest(noAutoStartup = {"inboundChannelAdapter", "*Source*"})
@Transactional
class JobProcessorIntegrationTest {
    private Random random = new Random();

    private JobProcessor cut;       // class under test

    @Autowired
    private FileService fileService;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private SponsorRepository sponsorRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private JobOutputRepository jobOutputRepository;
    @Autowired
    private RoundRobinThreadBroker roundRobinThreadBroker;
    @Autowired
    @Qualifier("contractAdapterStub")
    private ContractAdapter contractAdapterStub;
    @Autowired
    private OptOutRepository optOutRepository;

    @Mock
    private BFDClient mockBfdClient;

    @TempDir
    File tmpEfsMountDir;

    private Sponsor sponsor;
    private User user;
    private Job job;

    @Container
    private static final PostgreSQLContainer postgreSQLContainer = new AB2DPostgresqlContainer();
    private Bundle bundle1;
    private Bundle[] bundles;
    private RuntimeException fail;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        userRepository.deleteAll();
        sponsorRepository.deleteAll();

        sponsor = createSponsor();
        user = createUser(sponsor);
        job = createJob(user);

        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);
        createContract(sponsor);

        ExplanationOfBenefit eob = EobTestDataUtil.createEOB();
        bundle1 = EobTestDataUtil.createBundle(eob.copy());
        bundles = getBundles();
        when(mockBfdClient.requestEOBFromServer(anyString())).thenReturn(bundle1);

        fail = new RuntimeException("TEST EXCEPTION");

        FhirContext fhirContext = FhirContext.forDstu3();
        PatientClaimsProcessor patientClaimsProcessor = new PatientClaimsProcessorImpl(mockBfdClient, fhirContext);

        cut = new JobProcessorImpl(fileService, jobRepository, jobOutputRepository, roundRobinThreadBroker, contractAdapterStub,
                optOutRepository);
        ReflectionTestUtils.setField(cut, "cancellationCheckFrequency", 10);
        ReflectionTestUtils.setField(cut, "efsMount", tmpEfsMountDir.toString());
        ReflectionTestUtils.setField(cut, "failureThreshold", 10);
    }

    @Test
    @DisplayName("When a job is in submitted status, it can be processed")
    void processJob() {
        var processedJob = cut.process("S0000");

        assertThat(processedJob.getStatus(), is(JobStatus.SUCCESSFUL));
        assertThat(processedJob.getStatusMessage(), is("100%"));
        assertThat(processedJob.getExpiresAt(), notNullValue());
        assertThat(processedJob.getCompletedAt(), notNullValue());

        final List<JobOutput> jobOutputs = job.getJobOutputs();
        assertFalse(jobOutputs.isEmpty());
    }

    @Test
    @DisplayName("When a job is in submitted by the parent user, it process the contracts for the children")
    void whenJobSubmittedByParentUser_ProcessAllContractsForChildrenSponsors() {
        // switch the user to the parent sponsor
        user.setSponsor(sponsor.getParent());
        userRepository.save(user);

        var processedJob = cut.process("S0000");

        assertThat(processedJob.getStatus(), is(JobStatus.SUCCESSFUL));
        assertThat(processedJob.getStatusMessage(), is("100%"));
        assertThat(processedJob.getExpiresAt(), notNullValue());
        assertThat(processedJob.getCompletedAt(), notNullValue());

        final List<JobOutput> jobOutputs = job.getJobOutputs();
        assertFalse(jobOutputs.isEmpty());
    }

    @Test
    @DisplayName("When the error count is below threshold, job does not fail")
    void when_errorCount_is_below_threshold_do_not_fail_job() {
        when(mockBfdClient.requestEOBFromServer(anyString()))
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundle1, bundle1, bundle1, bundle1)
                .thenThrow(fail, fail, fail, fail, fail)
                ;

        var processedJob = cut.process("S0000");

        assertThat(processedJob.getStatus(), is(JobStatus.SUCCESSFUL));
        assertThat(processedJob.getStatusMessage(), is("100%"));
        assertThat(processedJob.getExpiresAt(), notNullValue());
        assertThat(processedJob.getCompletedAt(), notNullValue());

        final List<JobOutput> jobOutputs = job.getJobOutputs();
        assertFalse(jobOutputs.isEmpty());
    }

    @Test
    @DisplayName("When the error count is greater than or equal to threshold, job should fail")
    void when_errorCount_is_not_below_threshold_fail_job() {
        when(mockBfdClient.requestEOBFromServer(anyString()))
                .thenReturn(bundle1, bundles)
                .thenReturn(bundle1, bundles)
                .thenThrow(fail, fail, fail, fail, fail, fail, fail, fail, fail, fail)
                .thenReturn(bundle1, bundles)
        ;

        var processedJob = cut.process("S0000");

        assertThat(processedJob.getStatus(), is(JobStatus.FAILED));
        assertThat(processedJob.getStatusMessage(), is("Too many patient records in the job had failures"));
        assertThat(processedJob.getExpiresAt(), nullValue());
        assertThat(processedJob.getCompletedAt(), notNullValue());
    }

    private Bundle[] getBundles() {
        return new Bundle[]{bundle1, bundle1, bundle1, bundle1, bundle1, bundle1, bundle1, bundle1, bundle1};
    }

    private Sponsor createSponsor() {
        Sponsor parent = new Sponsor();
        parent.setOrgName("Parent");
        parent.setLegalName("Parent");
        parent.setHpmsId(350);

        Sponsor sponsor = new Sponsor();
        sponsor.setOrgName("Hogwarts School of Wizardry");
        sponsor.setLegalName("Hogwarts School of Wizardry LLC");
        sponsor.setHpmsId(random.nextInt());
        sponsor.setParent(parent);
        parent.getChildren().add(sponsor);
        return sponsorRepository.save(sponsor);
    }

    private User createUser(Sponsor sponsor) {
        User user = new User();
        user.setUsername("Harry_Potter");
        user.setFirstName("Harry");
        user.setLastName("Potter");
        user.setEmail("harry_potter@hogwarts.com");
        user.setEnabled(TRUE);
        user.setSponsor(sponsor);
        return userRepository.save(user);
    }

    private Contract createContract(Sponsor sponsor) {
        Contract contract = new Contract();
        contract.setContractName("CONTRACT_0000");
        contract.setContractNumber("CONTRACT_0000");
        contract.setAttestedOn(OffsetDateTime.now().minusDays(10));
        contract.setSponsor(sponsor);

        sponsor.getContracts().add(contract);
        return contractRepository.save(contract);
    }

    private Job createJob(User user) {
        Job job = new Job();
        job.setJobUuid("S0000");
        job.setStatus(JobStatus.SUBMITTED);
        job.setStatusMessage("0%");
        job.setUser(user);
        job.setOutputFormat(NDJSON_FIRE_CONTENT_TYPE);
        job.setCreatedAt(OffsetDateTime.now());
        return jobRepository.save(job);
    }
}