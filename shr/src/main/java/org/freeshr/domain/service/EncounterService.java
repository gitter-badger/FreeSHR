package org.freeshr.domain.service;

import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.infrastructure.persistence.EncounterRepository;
import org.freeshr.infrastructure.tr.TerminologyServer;
import org.hl7.fhir.instance.model.Condition;
import org.hl7.fhir.instance.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureAdapter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class EncounterService {

    private EncounterRepository encounterRepository;
    private PatientRegistry patientRegistry;
    private TerminologyServer terminologyServer;

    @Autowired
    public EncounterService(EncounterRepository encounterRepository, PatientRegistry patientRegistry, TerminologyServer terminologyServer) {
        this.encounterRepository = encounterRepository;
        this.patientRegistry = patientRegistry;
        this.terminologyServer = terminologyServer;
    }

    public ListenableFuture<String> ensureCreated(final EncounterBundle encounterBundle) throws ExecutionException, InterruptedException {
        encounterBundle.validate(terminologyServer);
        return new ListenableFutureAdapter<String, Boolean>(patientRegistry.ensurePresent(encounterBundle.getHealthId())) {
            @Override
            protected String adapt(Boolean result) throws ExecutionException {
                if (result) {
                    encounterBundle.setEncounterId(UUID.randomUUID().toString());
                    encounterRepository.save(encounterBundle);
                    return encounterBundle.getEncounterId();
                }
                return null;
            }
        };
    }

    public ListenableFuture<List<EncounterBundle>> findAll(String healthId) {
        return new ListenableFutureAdapter<List<EncounterBundle>, List<EncounterBundle>>(encounterRepository.findAll(healthId)) {
            @Override
            protected List<EncounterBundle> adapt(List<EncounterBundle> bundles) throws ExecutionException {
                return bundles;
            }
        };
    }
}