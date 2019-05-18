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
package com.enioka.api.admin;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

import com.enioka.api.helpers.BaseParameterDto;

/**
 * The association of a {@link ResourceManagerDto} with a {@link QueueMappingDto}.
 */
@XmlRootElement
public class ResourceManagerPollerMappingDto extends BaseParameterDto implements Serializable
{
    private static final long serialVersionUID = -5750890125510347623L;

    private Integer resourceManagerId;
    private Integer pollerId;

    /**
     * ID of the poller or node using the resource manager.
     */
    public Integer getPollerId()
    {
        return pollerId;
    }

    /**
     * ID of the poller or node using the resource manager.
     */
    public void setPollerId(Integer pollerId)
    {
        this.pollerId = pollerId;
    }

    /**
     * The resource manager being used.
     */
    public Integer getResourceManagerId()
    {
        return resourceManagerId;
    }

    /**
     * The resource manager being used.
     */
    public void setResourceManagerId(Integer rmId)
    {
        this.resourceManagerId = rmId;
    }
}
