package com.enioka.jqm.tools;

import com.beust.jcommander.Parameters;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.Node;

@Parameters(commandNames = "Get-NodeCount", commandDescription = "Get how many nodes are declared inside the central configuration.")
class CommandGetNodeCount extends CommandBase
{
    @Override
    int doWork()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            jqmlogger.info("Existing nodes: " + Node.getNodes(cnx).size());
            return 0;
        }
        catch (Exception e)
        {
            jqmlogger.error("Could not fetch node count", e);
            return 111;
        }
    }
}
