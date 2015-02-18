package org.freeshr.infrastructure.tr;

import org.freeshr.config.SHRProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.AsyncRestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.freeshr.infrastructure.tr.HttpCodeValidator.CONCEPT_PATTERN;
import static org.freeshr.infrastructure.tr.HttpCodeValidator.REF_TERM_PATTERN;
import static org.freeshr.infrastructure.tr.MedicationCodeValidator.MEDICATION_URL_PATTERN;
import static org.freeshr.infrastructure.tr.ValueSetCodeValidator.VALUE_SET_PATTERN;

@Component
public class CodeValidatorFactory {

    private Map<String, CodeValidator> codeValidatorMap;
    private Properties fhirTrMap;

    @Autowired
    public CodeValidatorFactory(AsyncRestTemplate shrRestTemplate,
                                SHRProperties shrProperties,
                                ValueSetCodeValidator valueSetCodeValidator,
                                MedicationCodeValidator medicationCodeValidator,
                                @Qualifier("fhirTrMap")Properties fhirTrMap) {
        codeValidatorMap = new HashMap<>();
        codeValidatorMap.put(REF_TERM_PATTERN, new HttpCodeValidator(shrRestTemplate, shrProperties, "code"));
        codeValidatorMap.put(CONCEPT_PATTERN, new HttpCodeValidator(shrRestTemplate, shrProperties, "uuid"));
        codeValidatorMap.put(VALUE_SET_PATTERN, valueSetCodeValidator);
        codeValidatorMap.put(MEDICATION_URL_PATTERN, medicationCodeValidator);
        this.fhirTrMap = fhirTrMap;

    }


    public CodeValidator getValidator(String url) {
        String fhirTrMapping = (String) fhirTrMap.get(url);
        url = fhirTrMapping != null ? fhirTrMapping : url;
        for (String urlKey : codeValidatorMap.keySet()) {
            if (url.contains(urlKey)) {
                return codeValidatorMap.get(urlKey);
            }
        }
        return null;
    }

}
