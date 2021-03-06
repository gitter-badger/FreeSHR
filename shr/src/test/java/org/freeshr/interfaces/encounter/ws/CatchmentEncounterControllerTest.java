package org.freeshr.interfaces.encounter.ws;


import com.sun.syndication.feed.atom.Feed;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.config.SHRProperties;
import org.freeshr.domain.service.CatchmentEncounterService;
import org.freeshr.events.EncounterEvent;
import org.freeshr.infrastructure.security.TokenAuthentication;
import org.freeshr.infrastructure.security.UserInfo;
import org.freeshr.infrastructure.security.UserProfile;
import org.freeshr.interfaces.encounter.ws.exceptions.BadRequest;
import org.freeshr.interfaces.encounter.ws.exceptions.ErrorInfo;
import org.freeshr.utils.Confidentiality;
import org.freeshr.utils.DateUtil;
import org.freeshr.utils.TimeUuidUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.async.DeferredResult;
import rx.Observable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CatchmentEncounterControllerTest {

    @Mock
    CatchmentEncounterService mockCatchmentEncounterService;

    @Mock
    private SHRProperties shrProperties;

    @Mock
    private SecurityContext securityContext;

    private CatchmentEncounterController controller;

    @Before
    public void setUp() {
        initMocks(this);
        controller = new CatchmentEncounterController(mockCatchmentEncounterService);
    }

    @Test
    public void shouldGetPagedEncountersForCatchment() throws Exception {
        int encounterFetchLimit = CatchmentEncounterService.getEncounterFetchLimit();
        List<Date> encounterDates = getTimeInstances(DateUtil.parseDate("2014-10-10"), 50);
        List<EncounterEvent> encounterEvents = createEncounterEvents("hid01", 50, encounterDates);

        ArrayList<String> datasenseFacilityCodes = new ArrayList<>();
        datasenseFacilityCodes.add("1232");
        TokenAuthentication tokenAuthentication = tokenAuthentication();
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(tokenAuthentication);
        when(mockCatchmentEncounterService.findEncounterFeedForFacilityCatchment(anyString(),
                any(Date.class),
                eq(encounterFetchLimit * 2))).thenReturn(Observable.just(slice(encounterFetchLimit * 2,
                encounterEvents)));

        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest(null, null,
                "/catchments/3026/encounters");
        DeferredResult<EncounterSearchResponse> encountersForCatchment = controller.findEncounterFeedForCatchment
                (mockHttpServletRequest, "3026", "2014-10-10", null);
        EncounterSearchResponse response = (EncounterSearchResponse) encountersForCatchment.getResult();
        List<EncounterEvent> entries = response.getEntries();
        assertEquals(encounterFetchLimit, entries.size());
        String expectedNextUrl = response.getNextUrl();
        List<NameValuePair> params = URLEncodedUtils.parse(new URI(expectedNextUrl), "UTF-8");
        //generated time uuid for entry at the fetch limit.
        String timeUUidFor20thEntry = TimeUuidUtil.uuidForDate(encounterDates.get(19)).toString();
        assertEquals(timeUUidFor20thEntry, params.get(1).getValue());

        String timeUUidFor22ndEntry = TimeUuidUtil.uuidForDate(encounterDates.get(21)).toString();

        encountersForCatchment = controller.findEncounterFeedForCatchment(mockHttpServletRequest, "3026",
                "2014-10-10", timeUUidFor22ndEntry);
        response = (EncounterSearchResponse) encountersForCatchment.getResult();
        entries = response.getEntries();
        assertEquals(18, entries.size());
    }

    @Test
    public void shouldGetAtomFeed() throws Exception {
        int encounterFetchLimit = CatchmentEncounterService.getEncounterFetchLimit();
        List<Date> encounterDates = getTimeInstances(DateUtil.parseDate("2014-10-10"), 50);
        List<EncounterEvent> encounterEvents = createEncounterEvents("hid01", 50, encounterDates);

        TokenAuthentication tokenAuthentication = tokenAuthentication();
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(tokenAuthentication);
        when(mockCatchmentEncounterService.findEncounterFeedForFacilityCatchment(anyString(),
                any(Date.class),
                eq(encounterFetchLimit * 2))).thenReturn(Observable.just(slice(encounterFetchLimit * 2,
                encounterEvents)));


        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest(null, null,
                "/catchments/3026/encounters");
        DeferredResult<EncounterSearchResponse> encountersForCatchment = controller.findEncounterFeedForCatchment
                (mockHttpServletRequest, "3026", "2014-10-10", null);
        EncounterSearchResponse response = (EncounterSearchResponse) encountersForCatchment.getResult();
        List<EncounterEvent> results = response.getEntries();

        EncounterFeedHelper encounterFeedBuilder = new EncounterFeedHelper();
        Feed feed = encounterFeedBuilder.generateFeed(response, generateFeedId("2014-10-10", null));
        assertEquals(encounterFetchLimit, feed.getEntries().size());
    }

    @Test
    public void shouldRollOverForNextUrl() throws UnsupportedEncodingException, URISyntaxException {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest(null, null,
                "/catchments/3026/encounters");
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        calendar.set(Calendar.YEAR, currentYear - 1);
        String nextResultURL = controller.getNextResultURL(mockHttpServletRequest, new ArrayList<EncounterEvent>(),
                calendar.getTime());
        List<NameValuePair> params = URLEncodedUtils.parse(new URI(nextResultURL), "UTF-8");

        calendar.add(Calendar.MONTH, 1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        String expected = new SimpleDateFormat("YYYY-MM-dd").format(calendar.getTime());
        assertTrue("Should have rolled over to next month: " + expected, params.get(0).getValue().startsWith(expected));

        Calendar futureDate = Calendar.getInstance();
        futureDate.add(Calendar.YEAR, 1);
        nextResultURL = controller.getNextResultURL(mockHttpServletRequest, new ArrayList<EncounterEvent>(),
                futureDate.getTime());
        assertNull("For future year, should have returned null", nextResultURL);

        nextResultURL = controller.getNextResultURL(mockHttpServletRequest, new ArrayList<EncounterEvent>(),
                Calendar.getInstance().getTime());
        assertNull("For current year, should have returned null", nextResultURL);
    }

    @Test
    public void shouldDefaultToStartOfMonthIfNotSpecified() throws UnsupportedEncodingException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(controller.getRequestedDateForCatchment(""));
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void shouldThrowErrorIfCatchmentDoesNotHaveDivisionAndDistrict() throws Exception {
        TokenAuthentication tokenAuthentication = tokenAuthentication();
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(tokenAuthentication);

        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest(null, null,
                "/catchments/30/encounters");
        DeferredResult<EncounterSearchResponse> encountersForCatchment = controller.findEncounterFeedForCatchment
                (mockHttpServletRequest, "30", "2014-10-10", null);
        assertTrue(encountersForCatchment.getResult() instanceof BadRequest);
    }

    private String generateFeedId(String updatedSince, String requestedMarker) {
        return StringUtils.isBlank(requestedMarker) ? "E-" + updatedSince : "E-" + updatedSince + "%2B" +
                requestedMarker;
    }

    private List<EncounterEvent> slice(int size, List<EncounterEvent> encounterEvents) {
        return encounterEvents.subList(0, size);
    }


    private List<EncounterEvent> createEncounterEvents(String healthId, int size, List<Date> dates) {

        List<EncounterEvent> encounterEvents = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            EncounterBundle encounter = new EncounterBundle();
            encounter.setEncounterId("e-" + (i + 1));
            encounter.setHealthId(healthId);
            encounter.setReceivedAt(dates.get(i));
            encounter.setUpdatedAt(dates.get(i));
            encounter.setEncounterContent("content-" + (i + 1));
            encounter.setPatientConfidentiality(Confidentiality.Normal);
            encounter.setEncounterConfidentiality(Confidentiality.Normal);

            EncounterEvent event = new EncounterEvent(dates.get(i), encounter);
            encounterEvents.add(event);
        }
        return encounterEvents;
    }

    private List<Date> getTimeInstances(Date startingFrom, int size) {
        List<Date> dates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startingFrom);
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);

        for (int i = 0; i < size; i++) {
            calendar.set(Calendar.SECOND, i);
            dates.add(calendar.getTime());
        }
        return dates;
    }

    @Test
    public void shouldSerializeErrorInfo() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        ErrorInfo errorInfo = new ErrorInfo(HttpStatus.NOT_FOUND, "Not found");
        MockHttpOutputMessage outputMessage = new MockHttpOutputMessage();
        try {
            converter.write(errorInfo, MediaType.APPLICATION_JSON, outputMessage);
            assertEquals("{\"httpStatus\":\"404\",\"message\":\"Not found\"}", outputMessage.getBodyAsString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private TokenAuthentication tokenAuthentication() {
        return new TokenAuthentication(new UserInfo("1232", "foo", "email@gmail.com", 1, true,
                "xyz", new ArrayList<String>(), asList(new UserProfile("facility", "10000069", asList("3026")))), true);
    }
}
