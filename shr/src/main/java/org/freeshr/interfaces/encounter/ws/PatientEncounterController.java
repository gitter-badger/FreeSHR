package org.freeshr.interfaces.encounter.ws;

import org.apache.commons.lang3.StringUtils;
import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.application.fhir.EncounterResponse;
import org.freeshr.domain.service.PatientEncounterService;
import org.freeshr.events.EncounterEvent;
import org.freeshr.infrastructure.security.UserInfo;
import org.freeshr.interfaces.encounter.ws.exceptions.Forbidden;
import org.freeshr.interfaces.encounter.ws.exceptions.ResourceNotFound;
import org.freeshr.utils.DateUtil;
import org.freeshr.utils.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import rx.Observable;
import rx.functions.Action1;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;
import static java.lang.String.format;
import static org.freeshr.infrastructure.security.AccessFilter.*;

@RestController
@RequestMapping(value = "/patients")
public class PatientEncounterController extends ShrController {
    private static final Logger logger = LoggerFactory.getLogger(PatientEncounterController.class);

    private PatientEncounterService patientEncounterService;

    @Autowired
    public PatientEncounterController(PatientEncounterService patientEncounterService) {
        this.patientEncounterService = patientEncounterService;
    }

    @PreAuthorize("hasAnyRole('ROLE_SHR_FACILITY', 'ROLE_SHR_PROVIDER')")
    @RequestMapping(value = "/{healthId}/encounters", method = RequestMethod.POST)
    public DeferredResult<EncounterResponse> create(
            @PathVariable String healthId,
            @RequestBody EncounterBundle encounterBundle) throws ExecutionException, InterruptedException {
        UserInfo userInfo = getUserInfo();
        logAccessDetails(userInfo, String.format("Create encounter request for patient (healthId) %s", healthId));
        final DeferredResult<EncounterResponse> deferredResult = new DeferredResult<>();

        try {
            logger.debug(String.format("Create encounter for patient (healthId) %s (encounter-content) %s", healthId, encounterBundle.getContent()));
            encounterBundle.setHealthId(healthId);
            Observable<EncounterResponse> encounterResponse = patientEncounterService.ensureCreated(encounterBundle,
                    userInfo);

            encounterResponse.subscribe(encounterSaveSuccessCallback(deferredResult), errorCallback(deferredResult));
        } catch (Exception e) {
            logger.debug(e.getMessage());
            deferredResult.setErrorResult(e);
        }
        return deferredResult;
    }


    @PreAuthorize("hasAnyRole('ROLE_SHR_FACILITY', 'ROLE_SHR_PROVIDER')")
    @RequestMapping(value = "/{healthId}/encounters/{encounterId}", method = RequestMethod.PUT)
    public DeferredResult<EncounterResponse> update(
            @PathVariable String healthId,
            @PathVariable final String encounterId,
            @RequestBody final EncounterBundle encounterBundle) throws ExecutionException, InterruptedException {
        final UserInfo userInfo = getUserInfo();
        logAccessDetails(userInfo, String.format("Create encounter request for patient (healthId) %s", healthId));
        final DeferredResult<EncounterResponse> deferredResult = new DeferredResult<>();

        try {
            encounterBundle.setHealthId(healthId);
            encounterBundle.setEncounterId(encounterId);
            logger.debug(String.format("Update encounter %s %s)", encounterId, encounterBundle.getContent()));
            Observable<EncounterResponse> encounterResponseObservable = patientEncounterService.ensureUpdated(encounterBundle, userInfo);
            encounterResponseObservable.subscribe(encounterSaveSuccessCallback(deferredResult), errorCallback(deferredResult));

        } catch (Exception e) {
            logger.debug(e.getMessage());
            deferredResult.setErrorResult(e);
        }
        return deferredResult;
    }

    @PreAuthorize("hasAnyRole('ROLE_SHR_FACILITY', 'ROLE_SHR_PROVIDER', 'ROLE_SHR_PATIENT', 'ROLE_SHR System Admin')")
    @RequestMapping(value = "/{healthId}/encounters", method = RequestMethod.GET,
            produces = {"application/json","application/atom+xml"})
    public DeferredResult<EncounterSearchResponse> findEncounterFeedForPatient(
            final HttpServletRequest request,
            @PathVariable final String healthId,
            @RequestParam(value = "updatedSince", required = false) String updatedSince) {
        logger.debug(String.format("Find all encounters by health id: %s", healthId));
        final UserInfo userInfo = getUserInfo();
        logAccessDetails(userInfo, String.format("Find all encounters of patient (healthId) %s", healthId));
        final DeferredResult<EncounterSearchResponse> deferredResult = new DeferredResult<>();

        try {
            final Boolean isRestrictedAccess = isAccessRestrictedToEncounterFetchForPatient(healthId, userInfo);
            if (isRestrictedAccess == null) {
                deferredResult.setErrorResult(new Forbidden(String.format("Access is denied to user %s for patient %s", userInfo.getProperties().getId(), healthId)));
                return deferredResult;
            }
            final Date requestedDate = getRequestedDate(updatedSince);
            Observable<List<EncounterEvent>> encounterEventsForPatient =
                    patientEncounterService.findEncounterFeedForPatient(healthId, requestedDate, 200);
            encounterEventsForPatient.subscribe(new Action1<List<EncounterEvent>>() {
                @Override
                public void call(List<EncounterEvent> encounterEvents) {
                    try {

                        List<EncounterBundle> encounterBundles = extract(encounterEvents, on(EncounterEvent.class).getEncounterBundle());
                        if (isRestrictedAccess && isConfidentialPatient(encounterBundles)) {
                            deferredResult.setErrorResult(new Forbidden(format("Access is denied to user %s for patient %s", userInfo.getProperties().getId(), healthId)));
                        } else {
                            encounterEvents = filterEncounterEvents(isRestrictedAccess, encounterEvents);
                            EncounterSearchResponse searchResponse = new EncounterSearchResponse(
                                    UrlUtil.addLastUpdatedQueryParams(request, requestedDate, null), encounterEvents);
                            logger.debug(searchResponse.toString());
                            deferredResult.setResult(searchResponse);
                        }
                    } catch (UnsupportedEncodingException e) {
                        deferredResult.setErrorResult(e);
                    }

                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    deferredResult.setErrorResult(throwable);
                }
            });
        } catch (Exception e) {
            logger.debug(e.getMessage());
            deferredResult.setErrorResult(e);
        }
        return deferredResult;
    }

    @PreAuthorize("hasAnyRole('ROLE_SHR_FACILITY', 'ROLE_SHR_PROVIDER', 'ROLE_SHR_PATIENT', 'ROLE_SHR System Admin')")
    @RequestMapping(value = "/{healthId}/encounters/{encounterId}", method = RequestMethod.GET,
            produces = {"application/json", "application/xml"})
    public DeferredResult<EncounterBundle> findEncountersForPatientByEncounterId(
            @PathVariable String healthId, @PathVariable final String encounterId) {
        final DeferredResult<EncounterBundle> deferredResult = new DeferredResult<>();
        logger.debug(format("Find encounter %s for patient %s", encounterId, healthId));
        final UserInfo userInfo = getUserInfo();
        logAccessDetails(userInfo, format("Find encounter %s for patient %s", encounterId, healthId));

        try {
            final Boolean isRestrictedAccess = isAccessRestrictedToEncounterFetchForPatient(healthId, userInfo);
            Observable<EncounterBundle> observable = patientEncounterService.findEncounter(healthId,
                    encounterId).firstOrDefault(null);
            observable.subscribe(new Action1<EncounterBundle>() {
                 @Override
                 public void call(EncounterBundle encounterBundle) {
                     if (encounterBundle != null) {
                         logger.debug(String.format("Encounter bundles: %s", encounterBundle.toString()));
                         if ((isRestrictedAccess == null || isRestrictedAccess) && encounterBundle.isConfidentialEncounter()) {
                             deferredResult.setErrorResult(new Forbidden(format("Access is denied to user %s for encounter %s",
                                     userInfo.getProperties().getId(), encounterId)));
                         } else {
                             deferredResult.setResult(encounterBundle);
                         }
                     } else {
                         deferredResult.setErrorResult(new ResourceNotFound(format("Encounter %s not found", encounterId)));
                     }
                 }
             },
            new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    deferredResult.setErrorResult(throwable);
                }
            });
        } catch (Exception e) {
            logger.debug(e.getMessage());
            deferredResult.setErrorResult(e);
        }
        return deferredResult;
    }

    private Action1<Throwable>  errorCallback(final DeferredResult<EncounterResponse> deferredResult) {
        return new Action1<Throwable>() {
            @Override
            public void call(Throwable error) {
                deferredResult.setErrorResult(error);
            }
        };
    }

    private Action1<EncounterResponse> encounterSaveSuccessCallback(final DeferredResult<EncounterResponse> deferredResult) {
        return new Action1<EncounterResponse>() {
            @Override
            public void call(EncounterResponse encounterResponse) {
                if (encounterResponse.isSuccessful()) {
                    logger.debug(encounterResponse.toString());
                    deferredResult.setResult(encounterResponse);
                } else {
                    deferredResult.setErrorResult(encounterResponse.getErrorResult());
                }
            }
        };
    }

    private Date getRequestedDate(String updatedSince) {
        return StringUtils.isBlank(updatedSince) ? null : DateUtil.parseDate(updatedSince);
    }
}
