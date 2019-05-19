package com.enioka.jqm.tools;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import com.enioka.api.admin.ResourceManagerDto;

/**
 * Helper holder for all {@link ResourceManagerBase} instances.
 */
class ResourceManagerManager
{
    private Map<Integer, ResourceManagerBase> instances = new HashMap<>();
    private ClassLoader extCl, engineCl;

    ResourceManagerManager(ClassLoader extCl)
    {
        this.extCl = extCl;
        this.engineCl = ResourceManagerManager.class.getClassLoader();
    }

    /**
     * Uses cache. Not thread-safe.
     *
     * @return the requested RM.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    ResourceManagerBase getResourceManager(ResourceManagerDto rmDefinition)
    {
        if (instances.containsKey(rmDefinition.getId()))
        {
            return instances.get(rmDefinition.getId());
        }

        Class rmClass = null;
        try
        {
            rmClass = this.extCl.loadClass(rmDefinition.getImplementation());
        }
        catch (ClassNotFoundException e)
        {
            try
            {
                rmClass = this.engineCl.loadClass(rmDefinition.getImplementation());
            }
            catch (ClassNotFoundException e2)
            {
                throw new JqmInitError("Missing resource manager class " + rmDefinition.getImplementation(), e2);
            }
        }
        catch (Exception e)
        {
            throw new JqmInitError("Class exists but could not load class " + rmDefinition.getImplementation(), e);
        }

        Constructor constructor;
        try
        {
            constructor = rmClass.getDeclaredConstructor(ResourceManagerDto.class);
        }
        catch (NoSuchMethodException e)
        {
            throw new JqmInitError(
                    "Class " + rmDefinition.getImplementation() + " does not implement a constructor taking a ResourceManagerDto", e);
        }

        try
        {
            ResourceManagerBase res = (ResourceManagerBase) constructor.newInstance(rmDefinition);
            instances.put(rmDefinition.getId(), res);

            res.refreshConfiguration(rmDefinition);
            return res;
        }
        catch (Exception e)
        {
            throw new JqmInitError("Could not crate an instance of class " + rmDefinition.getImplementation(), e);
        }
    }
}
