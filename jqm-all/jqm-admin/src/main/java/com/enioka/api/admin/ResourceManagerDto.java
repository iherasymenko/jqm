/**
 * Copyright © 2019 enioka. All rights reserved
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
 * Resource managers are responsible for handling resources needed by job instances. They are called by a resource scheduler when it ponders
 * whether or not it should launch a new job instance. This class contains the main configuration related to such a resource manager.
 */
@XmlRootElement
public class ResourceManagerDto extends BaseParameterDto implements Serializable
{
    private static final long serialVersionUID = 4677043925807285233L;

    private String implementation;
    private String key;
    private String description;

    /**
     * An optional description of what the RM does.
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * See {@link #getDescription()}
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * A RM may have a key to distinguish it from other RM of the same type.
     */
    public String getKey()
    {
        return key;
    }

    /**
     * See {@link #getKey()}
     */
    public void setKey(String key)
    {
        this.key = key;
    }

    /**
     * How the RM is implemented - i.e. a Java class.
     */
    public String getImplementation()
    {
        return implementation;
    }

    /**
     * See {@link #getImplementation()}
     */
    public void setImplementation(String implementation)
    {
        this.implementation = implementation;
    }
}
