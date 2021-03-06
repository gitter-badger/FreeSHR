package org.freeshr.infrastructure.persistence;

import org.freeshr.config.SHRConfig;
import org.freeshr.config.SHREnvironmentMock;
import org.freeshr.domain.model.patient.Address;
import org.freeshr.domain.model.patient.Patient;
import org.freeshr.utils.Confidentiality;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cassandra.core.CqlOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(initializers = SHREnvironmentMock.class, classes = SHRConfig.class)
public class PatientRepositoryIntegrationTest {

    private final String healthId = "testHealthId";

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    @Qualifier("SHRCassandraTemplate")
    private CqlOperations cqlTemplate;

    @Test
    public void shouldFindPatientWithMatchingHealthId() throws ExecutionException, InterruptedException {
        patientRepository.save(patient(healthId, true)).toBlocking().first();
        Patient patient = patientRepository.find(healthId).toBlocking().first();
        assertNotNull(patient);
        assertThat(patient, is(patient(healthId, false)));
        assertThat(patient.getAddress(), is(address()));
        assertTrue(patient.getConfidentiality().equals(Confidentiality.VeryRestricted));

    }

    private Patient patient(String healthId, boolean confidential) {
        Patient patient = new Patient();
        patient.setHealthId(healthId);
        patient.setGender("1");
        patient.setAddress(address());
        patient.setConfidentiality(confidential);
        return patient;
    }

    private Address address() {
        Address address = new Address();
        address.setDistrict("district");
        address.setDivision("division");
        address.setLine("line");
        address.setUpazila("upazilla");
        address.setUnionOrUrbanWardId("union");
        address.setCityCorporation("cityCorporation");
        return address;
    }

    @Test
    public void shouldNotFindPatientWithoutMatchingHealthId() throws ExecutionException, InterruptedException {
        assertNull(patientRepository.find(healthId + "invalid").toBlocking().first());
    }

    @After
    public void teardown() {
        cqlTemplate.execute("truncate patient");
    }

}