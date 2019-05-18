/**
 * Copyright © 2013 enioka. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.enioka.jqm.tools;

import java.util.ArrayList;
import java.util.List;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.ResourceManagerDto;
import com.enioka.api.admin.ResourceManagerNodeMappingDto;
import com.enioka.api.admin.ResourceManagerPollerMappingDto;
import com.enioka.jqm.test.helpers.TestHelpers;

import org.junit.Assert;
import org.junit.Test;

public class MetaServiceTest extends JqmBaseTest
{
    @Test
    public void testRMPersistence() throws Exception
    {
        List<ResourceManagerDto> dtos = MetaService.getResourceManagers(cnx);
        Assert.assertEquals(0, dtos.size());

        // Create a single RM and check it is persisted
        ResourceManagerDto rm1 = new ResourceManagerDto();
        rm1.addParameter("p1", "v1");
        rm1.addParameter("p2", "v2");
        rm1.setDescription("description");
        rm1.setImplementation("com.marsupilami.rm1");
        MetaService.upsertResourceManager(cnx, rm1);

        dtos = MetaService.getResourceManagers(cnx);
        Assert.assertEquals(1, dtos.size());

        // Add a parameter to the RM
        rm1.addParameter("p3", "v3");
        MetaService.upsertResourceManager(cnx, rm1);

        dtos = MetaService.getResourceManagers(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(3, dtos.get(0).getParameters().size());

        // Remove a parameter
        rm1.removeParameter("p1");
        rm1.removeParameter("p2");
        MetaService.upsertResourceManager(cnx, rm1);

        dtos = MetaService.getResourceManagers(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(1, dtos.get(0).getParameters().size());
        Assert.assertEquals("v3", dtos.get(0).getParameters().get("p3"));

        // Change parameter value
        rm1.addParameter("p3", "v3_2");
        MetaService.upsertResourceManager(cnx, rm1);

        dtos = MetaService.getResourceManagers(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(1, dtos.get(0).getParameters().size());
        Assert.assertEquals("v3_2", dtos.get(0).getParameters().get("p3"));

        // Add another RM.
        ResourceManagerDto rm2 = new ResourceManagerDto();
        rm2.addParameter("p1", "v1");
        rm2.setDescription("description");
        rm2.setImplementation("com.marsupilami.rm2");
        MetaService.upsertResourceManager(cnx, rm2);

        dtos = MetaService.getResourceManagers(cnx);
        Assert.assertEquals(2, dtos.size());

        // Finally, test sync. Description is modified + one RM is removed.
        rm1.setDescription("description2");
        List<ResourceManagerDto> currentDtos = new ArrayList<>(1);
        currentDtos.add(rm1);
        MetaService.syncResourceManagers(cnx, currentDtos);

        dtos = MetaService.getResourceManagers(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals("description2", dtos.get(0).getDescription());
    }

    @Test
    public void testRMNodeMappingPersistence() throws Exception
    {
        List<ResourceManagerNodeMappingDto> dtos = MetaService.getResourceManagerNodeMappings(cnx);
        Assert.assertEquals(0, dtos.size());

        // Create a single RM
        ResourceManagerDto rm1 = new ResourceManagerDto();
        rm1.addParameter("p1", "v1");
        rm1.addParameter("p2", "v2");
        rm1.setDescription("description");
        rm1.setImplementation("com.marsupilami.rm1");
        MetaService.upsertResourceManager(cnx, rm1);

        ResourceManagerNodeMappingDto rmm1 = new ResourceManagerNodeMappingDto();
        rmm1.setResourceManagerId(rm1.getId());
        rmm1.setNodeId(TestHelpers.node.getId());
        MetaService.upsertResourceManagerNodeMapping(cnx, rmm1);

        dtos = MetaService.getResourceManagerNodeMappings(cnx);
        Assert.assertEquals(1, dtos.size());

        // Add a parameter
        rmm1.addParameter("p3", "v3");
        rmm1.addParameter("p4", "v4");
        MetaService.upsertResourceManagerNodeMapping(cnx, rmm1);

        dtos = MetaService.getResourceManagerNodeMappings(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(2, dtos.get(0).getParameters().size());

        // Remove a parameter
        rmm1.removeParameter("p3");
        MetaService.upsertResourceManagerNodeMapping(cnx, rmm1);

        dtos = MetaService.getResourceManagerNodeMappings(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(1, dtos.get(0).getParameters().size());
        Assert.assertEquals("v4", dtos.get(0).getParameters().get("p4"));

        // Change parameter value
        rmm1.addParameter("p4", "v4_2");
        MetaService.upsertResourceManagerNodeMapping(cnx, rmm1);

        dtos = MetaService.getResourceManagerNodeMappings(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(1, dtos.get(0).getParameters().size());
        Assert.assertEquals("v4_2", dtos.get(0).getParameters().get("p4"));
    }

    @Test
    public void testRMPollerMappingPersistence() throws Exception
    {
        List<ResourceManagerPollerMappingDto> dtos = MetaService.getResourceManagerPollerMappings(cnx);
        Assert.assertEquals(0, dtos.size());

        // Create a single RM
        ResourceManagerDto rm1 = new ResourceManagerDto();
        rm1.addParameter("p1", "v1");
        rm1.addParameter("p2", "v2");
        rm1.setDescription("description");
        rm1.setImplementation("com.marsupilami.rm1");
        MetaService.upsertResourceManager(cnx, rm1);

        ResourceManagerPollerMappingDto rmp1 = new ResourceManagerPollerMappingDto();
        rmp1.setResourceManagerId(rm1.getId());
        rmp1.setPollerId(TestHelpers.dpNormal.getId());
        MetaService.upsertResourceManagerPollerMapping(cnx, rmp1);

        dtos = MetaService.getResourceManagerPollerMappings(cnx);
        Assert.assertEquals(1, dtos.size());

        // Add a parameter
        rmp1.addParameter("p3", "v3");
        rmp1.addParameter("p4", "v4");
        MetaService.upsertResourceManagerPollerMapping(cnx, rmp1);

        dtos = MetaService.getResourceManagerPollerMappings(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(2, dtos.get(0).getParameters().size());

        // Remove a parameter
        rmp1.removeParameter("p3");
        MetaService.upsertResourceManagerPollerMapping(cnx, rmp1);

        dtos = MetaService.getResourceManagerPollerMappings(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(1, dtos.get(0).getParameters().size());
        Assert.assertEquals("v4", dtos.get(0).getParameters().get("p4"));

        // Change parameter value
        rmp1.addParameter("p4", "v4_2");
        MetaService.upsertResourceManagerPollerMapping(cnx, rmp1);

        dtos = MetaService.getResourceManagerPollerMappings(cnx);
        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(1, dtos.get(0).getParameters().size());
        Assert.assertEquals("v4_2", dtos.get(0).getParameters().get("p4"));
    }
}
