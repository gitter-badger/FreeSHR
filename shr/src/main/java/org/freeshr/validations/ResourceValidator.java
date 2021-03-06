package org.freeshr.validations;


import org.hl7.fhir.instance.model.AtomEntry;
import org.hl7.fhir.instance.model.AtomFeed;
import org.hl7.fhir.instance.model.Resource;
import org.hl7.fhir.instance.model.ResourceType;
import org.hl7.fhir.instance.validation.ValidationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResourceValidator implements Validator<AtomFeed> {

    public static final String INVALID = "invalid";
    public static final String CODE_UNKNOWN = "code-unknown";

    private Map<ResourceType, Validator<AtomEntry<? extends Resource>>> resourceTypeValidatorMap = new HashMap<>();

    @Autowired
    public ResourceValidator(ConditionValidator conditionValidator,
                             MedicationPrescriptionValidator medicationPrescriptionValidator,
                             Validator<AtomEntry<? extends Resource>> immunizationValidator, Validator<AtomEntry<? extends Resource>>
                                     procedureValidator) {
        assignDefaultValidatorToAllResourceTypes();
        resourceTypeValidatorMap.put(ResourceType.Condition, conditionValidator);
        resourceTypeValidatorMap.put(ResourceType.MedicationPrescription, medicationPrescriptionValidator);
        resourceTypeValidatorMap.put(ResourceType.Immunization, immunizationValidator);
        resourceTypeValidatorMap.put(ResourceType.Procedure, procedureValidator);

    }

    private void assignDefaultValidatorToAllResourceTypes() {
        for (ResourceType resourceType : ResourceType.values()) {
            resourceTypeValidatorMap.put(resourceType, new DefaultValidator());
        }
    }

    @Override
    public List<ValidationMessage> validate(ValidationSubject<AtomFeed> subject) {
        AtomFeed feed = subject.extract();
        List<ValidationMessage> validationMessages = new ArrayList<>();

        for (final AtomEntry<? extends Resource> atomEntry : feed.getEntryList()) {
            Validator<AtomEntry<? extends Resource>> validator =
                    resourceTypeValidatorMap.get(atomEntry.getResource().getResourceType());
            validationMessages.addAll(validator.validate(atomEntryFragment(atomEntry)));
        }
        return validationMessages;
    }

    private ValidationSubject<AtomEntry<? extends Resource>>
    atomEntryFragment(final AtomEntry<? extends Resource> atomEntry) {
        return new ValidationSubject<AtomEntry<? extends Resource>>() {
            @Override
            public AtomEntry<? extends Resource> extract() {
                return atomEntry;
            }
        };
    }

}
