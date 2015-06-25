package info.bluefloyd.jenkins;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;


public class IssueUpdatesBuilderTest
{
	@Test
	public void testEnvVarSubstitution()
	{
		String jql = "$SQL_var $NO_ENV";  // NO_ENV does not exist.
		String comment = "$CMT _$$CMT $SQL_var $ActionName $VERSION2";
		String fieldId = "customfield_10862";
		String fieldValue = "$SQL_var ver $VERSION2";
		String workflowActionName ="  $ActionName $ ActionName";
		String fixedVersions = "v1,$VERSION2,$VERSIONS";
		
		Map<String, String> vars = new HashMap<String, String>();
		vars.put( "SQL_var", "some JQL" );
		vars.put( "CMT", "comment text" );
		vars.put( "ActionName", "ActionClose" );
		vars.put( "VERSION2", "v2" );
		vars.put( "VERSIONS", "v3,v4,v5" );
		
		IssueUpdatesBuilder builder = new IssueUpdatesBuilder( "soapUrl", "userName", "password", jql, workflowActionName, comment, fieldId, fieldValue, true, true, fixedVersions, true, true );
		Assert.assertEquals( "var1 var1 $var1", builder.substituteEnvVar( "$VAR $VAR $$VAR", "VAR", "var1"  ) );

		builder.substituteEnvVars( vars );
		
		final List<String> versionList = Arrays.asList( "v1", "v2", "v3", "v4", "v5" );
		Assert.assertTrue( versionList.containsAll( builder.fixedVersionNames ) );
		Assert.assertTrue( builder.fixedVersionNames.containsAll( versionList ) );
	}
}
