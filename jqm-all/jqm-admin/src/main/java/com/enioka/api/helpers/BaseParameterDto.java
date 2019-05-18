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
package com.enioka.api.helpers;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Helper base class to factor some code between DTO using ID and parameters.
 */
@XmlRootElement
public abstract class BaseParameterDto implements IWithId
{
    private Integer id;

    @XmlElementWrapper(name = "parameters")
    @XmlElement(name = "parameter")
    private Map<String, String> parameters = new HashMap<>();

    /**
     * @return Technical unique ID of the object. No meaning whatsoever.
     */
    public Integer getId()
    {
        return id;
    }

    /**
     * See {@link #getId()}
     */
    public void setId(Integer id)
    {
        this.id = id;
    }

    public Map<String, String> getParameters()
    {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters)
    {
        this.parameters = parameters;
    }

    /**
     * Helper method to add a parameter to the object.
     *
     * @param key
     *                  not null or empty.
     * @param value
     */
    public void addParameter(String key, String value)
    {
        if (key == null || key.trim().length() == 0)
        {
            throw new IllegalArgumentException("parameter key cannot be null or empty");
        }
        this.parameters.put(key, value);
    }

    /**
     * Helper method to remove a parameter from the object if it exists. Ignored if does not exist.
     *
     * @param key
     */
    public void removeParameter(String key)
    {
        if (key == null || key.trim().length() == 0)
        {
            throw new IllegalArgumentException("parameter key cannot be null or empty");
        }
        this.parameters.remove(key);
    }

    /**
     * Helper method.
     */
    public void clearParameters()
    {
        this.parameters.clear();
    }
}
