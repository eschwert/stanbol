/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.stanbol.enhancer.engines.dereference.entityhub;

import static org.apache.stanbol.enhancer.engines.dereference.DereferenceConstants.DEREFERENCE_ENTITIES_FIELDS;
import static org.apache.stanbol.enhancer.engines.dereference.DereferenceConstants.DEREFERENCE_ENTITIES_LDPATH;
import static org.apache.stanbol.enhancer.servicesapi.EnhancementEngine.PROPERTY_NAME;
import static org.osgi.framework.Constants.SERVICE_RANKING;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.stanbol.commons.namespaceprefix.NamespacePrefixService;
import org.apache.stanbol.enhancer.engines.dereference.DereferenceConstants;
import org.apache.stanbol.enhancer.engines.dereference.DereferenceUtils;
import org.apache.stanbol.enhancer.engines.dereference.EntityDereferenceEngine;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.ServiceProperties;
import org.apache.stanbol.entityhub.servicesapi.Entityhub;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The EntityhubLinkingEngine in NOT an {@link EnhancementEngine} but only an
 * OSGI {@link Component} that allows to configure instances of the
 * {@link EntityLinkingEngine} using an {@link SiteDereferencer} or
 * {@link EntityhubDereferencer} to link entities.
 * @author Rupert Westenthaler
 *
 */
@Component(
    configurationFactory = true, 
    policy = ConfigurationPolicy.REQUIRE, // the baseUri is required!
    specVersion = "1.1", 
    metatype = true, 
    immediate = true,
    inherit = true)
@org.apache.felix.scr.annotations.Properties(value={
    @Property(name=PROPERTY_NAME),
    @Property(name=EntityhubDereferenceEngine.SITE_ID),
    @Property(name=DEREFERENCE_ENTITIES_FIELDS,cardinality=Integer.MAX_VALUE,
    	value={"rdfs:comment","geo:lat","geo:long","foaf:depiction","dbp-ont:thumbnail"}),
    @Property(name=DEREFERENCE_ENTITIES_LDPATH, cardinality=Integer.MAX_VALUE),
    @Property(name=SERVICE_RANKING,intValue=0)
})
public class EntityhubDereferenceEngine implements ServiceTrackerCustomizer {

    private final Logger log = LoggerFactory.getLogger(EntityhubDereferenceEngine.class);

    @Reference(cardinality=ReferenceCardinality.OPTIONAL_UNARY)
    protected NamespacePrefixService prefixService;
    
    /**
     * The id of the Entityhub Site (Referenced or Managed Site) used for matching. <p>
     * To match against the Entityhub use "entityhub" as value.
     */
    public static final String SITE_ID = "enhancer.engines.dereference.entityhub.siteId";

    
    /**
     * The engine initialised based on the configuration of this component
     */
    protected EntityDereferenceEngine entityDereferenceEngine;
    protected Dictionary<String,Object> engineMetadata;
    /**
     * The service registration for the {@link #entityDereferenceEngine}
     */
    protected ServiceRegistration engineRegistration;
    /**
     * The EntitySearcher used for the {@link #entityDereferenceEngine}
     */
    private TrackingDereferencerBase<?> entityDereferencer;
    int trackedServiceCount = 0;
    
    /**
     * The name of the reference site ('local' or 'entityhub') if the
     * Entityhub is used for enhancing
     */
    protected String siteName;

    private BundleContext bundleContext;

    /**
     * Default constructor as used by OSGI. This expects that 
     * {@link #activate(ComponentContext)} is called before usage
     */
    public EntityhubDereferenceEngine() {
    }

    @Activate
    @SuppressWarnings("unchecked")
    protected void activate(ComponentContext ctx) throws ConfigurationException {
        Dictionary<String,Object> properties = ctx.getProperties();
        bundleContext = ctx.getBundleContext();
        log.info("> activate {}",getClass().getSimpleName());
        //get the metadata later set to the enhancement engine
        String engineName;
        engineMetadata = new Hashtable<String,Object>();
        Object value = properties.get(PROPERTY_NAME);
        if(value == null || value.toString().isEmpty()){
            throw new ConfigurationException(PROPERTY_NAME, "The EnhancementEngine name MUST BE configured!");
        } else {
            engineName = value.toString().trim();
        }
        log.debug(" - engineName: {}",engineName);
        engineMetadata.put(PROPERTY_NAME, engineName);
        value = properties.get(SERVICE_RANKING);
        Integer serviceRanking = null;
        if(value instanceof Number){
            serviceRanking = ((Number)value).intValue();
        } else if(value != null){
            try {
                serviceRanking = Integer.parseInt(value.toString());
            } catch(NumberFormatException e){
                throw new ConfigurationException(SERVICE_RANKING, "Parsed service ranking '"
                        + value + "' (type: " + value.getClass().getName()
                        + "' can not be converted to an integer value!", e);
            }
        } //else not defined
        if(serviceRanking != null){
            log.debug(" - service.ranking: {}", serviceRanking);
            engineMetadata.put(Constants.SERVICE_RANKING, serviceRanking);
        }
        //parse the Entityhub Site used for dereferencing
        value = properties.get(SITE_ID);
        //init the EntitySource
        if (value == null) {
            siteName = "*"; //all referenced sites
        } else {
            siteName = value.toString();
        }
        if (siteName.isEmpty()) {
            siteName = "*";
        }
        log.debug(" - siteName: {}", siteName);
        //init the tracking entity searcher
        trackedServiceCount = 0;
        if(Entityhub.ENTITYHUB_IDS.contains(siteName.toLowerCase())){
            log.info("  ... init Entityhub dereferencer");
            entityDereferencer = new EntityhubDereferencer(bundleContext, this, null);
        } else if(siteName.equals("*")){
            log.info("  ... init dereferencer for all referenced sites");
            entityDereferencer = new SitesDereferencer(bundleContext, this, null);
        } else {
            log.info(" ... init dereferencer for referenced site {}", siteName);
            entityDereferencer = new SiteDereferencer(bundleContext,siteName, this, null);
        }
        //set the namespace prefix service to the dereferencer
        entityDereferencer.setNsPrefixService(prefixService);
        //now parse dereference field config
        entityDereferencer.setDereferencedFields(
            DereferenceUtils.parseDereferencedFieldsConfig(properties));
        //create the engine
        entityDereferencer.setLdPath(
            DereferenceUtils.parseLdPathConfig(properties));
        entityDereferenceEngine = new EntityDereferenceEngine(engineName, entityDereferencer);
        //NOTE: registration of this instance as OSGI service is done as soon as the
        //      entityhub service backing the entityDereferencer is available.
        
        //finally start tracking
        entityDereferencer.open();
        
    }
    /**
     * Deactivates this components. 
     */
    @Deactivate
    protected void deactivate(ComponentContext context) {
        //* unregister service
        ServiceRegistration reg = engineRegistration;
        if(reg != null){
            reg.unregister();
            engineRegistration = null;
        }
        //* reset engine
        entityDereferenceEngine = null;
        engineMetadata = null;
        //close the tracking EntitySearcher
        entityDereferencer.close();
        entityDereferencer = null;
    }
    @Override
    public Object addingService(ServiceReference reference) {
        BundleContext bc = this.bundleContext;
        if(bc != null){
            Object service =  bc.getService(reference);
            if(service != null){
                if(trackedServiceCount == 0){
                    //register the service
                    engineRegistration = bc.registerService(
                        new String[]{EnhancementEngine.class.getName(),
                                     ServiceProperties.class.getName()},
                    entityDereferenceEngine,
                    engineMetadata);
                    
                }
                trackedServiceCount++;
            }
            return service;
        } else {
            return null;
        }
    }
    @Override
    public void modifiedService(ServiceReference reference, Object service) {
    }
    
    @Override
    public void removedService(ServiceReference reference, Object service) {
        BundleContext bc = this.bundleContext;
        if(bc != null){
            trackedServiceCount--;
            if(trackedServiceCount == 0 && engineRegistration != null){
               engineRegistration.unregister();
            }
            bc.ungetService(reference);
        }
    }
}