package org.freeshr.validations;

import org.freeshr.domain.service.EncounterService;
import org.hl7.fhir.instance.model.*;
import org.hl7.fhir.instance.validation.ValidationMessage;
import org.springframework.cache.annotation.Cacheable;

import java.util.ArrayList;
import java.util.List;

public class EncounterTypeValidator extends Validator {

    public EncounterTypeValidator() {
    }

    @Override
    public void validate(List<ValidationMessage> validationMessages, AtomEntry<? extends Resource> atomEntry) {
        String type = ((CodeableConcept) atomEntry.getResource().getChildByName("type").getValues().get(0)).getTextSimple();
        if(! findEncounterType().contains(type)) {
            validationMessages.add(new ValidationMessage(null, ResourceValidator.INVALID, atomEntry.getId(),
                    "Invalid Encounter Type", OperationOutcome.IssueSeverity.error));
        }
    }

    @Cacheable(value = "refData")
    public List<String> findEncounterType(){
        //Retrieve from TR and create a List
        return new ArrayList<String>();
    }

    @Override
    boolean skipCheckForThisTypeOfEntry(AtomEntry<? extends Resource> atomEntry) {
        return false;
    }
}
