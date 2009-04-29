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
package org.sakaiproject.kernel.messaging;

import org.osgi.service.component.ComponentContext;
import org.sakaiproject.kernel.api.jcr.JCRService;
import org.sakaiproject.kernel.api.messaging.Message;
import org.sakaiproject.kernel.api.messaging.MessageHandler;
import org.sakaiproject.kernel.api.messaging.MessagingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

/**
 * @scr.component
 */
public class OutboxListener implements EventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxListener.class);

  /** @scr.reference */
  private JCRService jcr;

  /**
   * @scr.reference
   *                interface="org.sakaiproject.kernel.api.messaging.MessageHandler"
   *                policy="dynamic" cardinality="0..n" bind="addHandler"
   *                unbind="removeHandler"
   */
  private List<MessageHandler> handlers = new ArrayList<MessageHandler>();

  /**
   * Default constructor
   */
  public OutboxListener() {
  }

  /**
   * Binder for adding message handlers.
   *
   * @param handler
   */
  protected void addHandler(MessageHandler handler) {
    handlers.add(handler);
  }

  /**
   * Unbinder for removing message handlers.
   *
   * @param handler
   */
  protected void removeHandler(MessageHandler handler) {
    handlers.remove(handler);
  }


  /**
   * Component activation.
   *
   * @param ctx
   */
  protected void activate(ComponentContext ctx) {
    try {
      ObservationManager obMgr = jcr.getObservationManager();
      obMgr.addEventListener(this, Event.NODE_ADDED, "/", true, null, new String[] {}, false);
    } catch (RepositoryException e) {
      // what to do?
    }
  }

  /**
   * Component deactivation.
   *
   * @param ctx
   */
  protected void deactivate(ComponentContext ctx) {
    try {
      ObservationManager obMgr = jcr.getObservationManager();
      obMgr.removeEventListener(this);
    } catch (RepositoryException e) {
      // nothing we can do
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.kernel.jcr.api.JcrContentListener#onEvent(int,
   *      java.lang.String, java.lang.String, java.lang.String)
   */
  public void onEvent(EventIterator events) {

    while (events.hasNext()) {
      try {
        Event event = events.nextEvent();
        String userID = event.getUserID();
        String filePath = event.getPath();
        // make sure we deal only with outbox items
        if (filePath.contains(MessagingConstants.FOLDER_MESSAGES + "/"
            + MessagingConstants.FOLDER_OUTBOX)) {
          LOGGER.debug("Handling outbox message [{0}]",filePath );
          // get the node, call up the appropriate handler and pass off based on
          // message type
          Session session = jcr.getSession();
          Node n = (Node) session.getItem(filePath);
          Property msgTypeProp = n.getProperty(Message.Field.TYPE.toString());
          String msgType = msgTypeProp.getString();
          boolean handled = false;
          if (msgType != null && handlers != null) {
            for (MessageHandler handler : handlers) {
              if (msgType.equalsIgnoreCase(handler.getType())) {
                LOGGER.debug("Handling with {0} : {1} ",msgType,handler);
                handler.handle(userID, filePath, null, n);
                handled = true;
              }
            }
          }
          if (!handled) {
            LOGGER.warn("No handler found for message type [{0}]",msgType);
          }
        }
      } catch (RepositoryException e) {
         LOGGER.error(e.getMessage(), e);
      }
    }
  }
}