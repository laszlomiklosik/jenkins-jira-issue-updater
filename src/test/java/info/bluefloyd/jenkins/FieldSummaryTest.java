package info.bluefloyd.jenkins;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bluefloyd.jira.model.IssueSummaryList;
import info.bluefloyd.jira.model.VersionSummary;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Test case for Issue
 *
 * https://issues.jenkins-ci.org/browse/JENKINS-31755
 *
 * @author ian
 */
public class FieldSummaryTest {

  /**
   * JSON parsing fails when managed versions are used. Fixed by adding
   * VersionSummary class to the model.
   * 
   * @throws java.io.IOException
   */
  @Test
  public void TestFieldSummaryWithVersions() throws IOException {

    String issueSummaryRESTResult = "{ \"expand\" : \"names,schema\",\n"
            + "  \"issues\" : [ { \"expand\" : \"operations,versionedRepresentations,editmeta,changelog,transitions,renderedFields\",\n"
            + "        \"fields\" : { \"summary\" : \"Check _52\",\n"
            + "            \"versions\" : [ { \"archived\" : false,\n"
            + "                  \"description\" : \"Demo version\",\n"
            + "                  \"id\" : \"10505\",\n"
            + "                  \"name\" : \"1.0\",\n"
            + "                  \"released\" : false,\n"
            + "                  \"self\" : \"http://benjira01.tally.tallysolutions.com:8080/rest/api/2/version/10505\"\n"
            + "                } ]\n"
            + "          },\n"
            + "        \"id\" : \"11274\",\n"
            + "        \"key\" : \"SA-52\",\n"
            + "        \"self\" : \"http://benjira01.tally.tallysolutions.com:8080/rest/api/2/issue/11274\"\n"
            + "      } ],\n"
            + "  \"maxResults\" : 1000,\n"
            + "  \"startAt\" : 0,\n"
            + "  \"total\" : 1\n"
            + "}";

    ObjectMapper mapper = new ObjectMapper();
    IssueSummaryList summaryList = mapper.readValue(issueSummaryRESTResult, IssueSummaryList.class);
    
    assertNotNull(summaryList);
    assertEquals(1,summaryList.getIssues().size());
    assertEquals(1,summaryList.getIssues().get(0).getFields().getVersions().size());
    VersionSummary version = summaryList.getIssues().get(0).getFields().getVersions().get(0);
    assertFalse(version.isArchived());
    assertFalse(version.isReleased());
    assertEquals("10505",version.getId());
  }
}
