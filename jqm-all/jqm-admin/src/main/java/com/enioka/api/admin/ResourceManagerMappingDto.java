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
 * The association of a {@link ResourceManagerDto} with a {@link NodeDto}.
 */
@XmlRootElement
public class ResourceManagerMappingDto extends BaseParameterDto implements Serializable
{
    private static final long serialVersionUID = -5750890125510347623L;

    public enum TargetType {
        NODE, POLLER
    }

    private Integer resourceManagerId;
    private Integer targetId;
    private TargetType targetType;

    public TargetType getTargetType()
    {
        return targetType;
    }

    public void setTargetType(TargetType targetType)
    {
        this.targetType = targetType;
    }

    public Integer getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Integer targetId)
    {
        this.targetId = targetId;
    }

    public Integer getResourceManagerId()
    {
        return resourceManagerId;
    }

    public void setResourceManagerId(Integer rmId)
    {
        this.resourceManagerId = rmId;
    }
}
