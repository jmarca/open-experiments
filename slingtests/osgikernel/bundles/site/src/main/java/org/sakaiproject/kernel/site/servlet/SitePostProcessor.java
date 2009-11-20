/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.kernel.site.servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.kernel.api.site.SiteService;
import org.sakaiproject.kernel.resource.AbstractVirtualResourcePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * 
 * @scr.service interface="org.apache.sling.servlets.post.SlingPostProcessor"
 * @scr.property name="service.vendor" value="The Sakai Foundation"
 * @scr.component immediate="true" label="SitePostProcessor"
 *                description="Post Processor for Site operations" metatype="no"
 * @scr.property name="service.description" value="Post Processes site operations"
 * 
 */
public class SitePostProcessor extends AbstractVirtualResourcePostProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(SitePostProcessor.class);

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.kernel.siteservice.AbstractResourceTypePostProcessor#getResourceType()
   */
  @Override
  protected String getResourceType() {
    return SiteService.SITE_RESOURCE_TYPE;
  }

  /**
   * At the moment this function does not do anything other than print out a log message.
   * If we need to do something on the site create, then we can put it here. 
   * 
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.kernel.siteservice.AbstractResourceTypePostProcessor#onCreate(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.servlets.post.Modification)
   */
  @Override
  protected void doProcess(SlingHttpServletRequest request, List<Modification> changes) {
    for (Modification m : changes) {
      try {
        Session s = request.getResourceResolver().adaptTo(Session.class);
        if (s.itemExists(m.getSource())) {
          Item item = s.getItem(m.getSource());
          if (item != null && item.isNode()) {
            LOGGER.info("Change to node {} " + item);
          } else {
            LOGGER.info("Change to property {} ", item);
          }
        }
      } catch (RepositoryException ex) {
        LOGGER.warn("Failed to process on create for {} ", m.getSource(), ex);
      }
    }
  }

}
