package org.freeshr.validations.ProviderSubResourceValidators;

import org.apache.commons.lang3.StringUtils;
import org.freeshr.utils.CollectionUtils;
import org.hl7.fhir.instance.model.Observation;
import org.hl7.fhir.instance.model.Resource;
import org.hl7.fhir.instance.model.ResourceReference;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ObservationPerformer extends SubResourceProvider {

    @Override
    boolean canHandle(Resource resource) {
        return (resource instanceof Observation);
    }

    @Override
    List<String> extractUrls(Resource resource) {
        List<ResourceReference> performers = ((Observation) resource).getPerformer();
        String url = null;
        if (!CollectionUtils.isEmpty(performers)) {
            url = performers.get(0).getReferenceSimple();
            url = url == null ? StringUtils.EMPTY : url;
        }
        return url == null ? null : Arrays.asList(url);
    }
}
