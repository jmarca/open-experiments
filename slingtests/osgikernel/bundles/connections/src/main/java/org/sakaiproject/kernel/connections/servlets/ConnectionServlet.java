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
package org.sakaiproject.kernel.connections.servlets;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.sakaiproject.kernel.api.connections.ConnectionException;
import org.sakaiproject.kernel.api.connections.ConnectionManager;
import org.sakaiproject.kernel.api.connections.ConnectionOperation;
import org.sakaiproject.kernel.api.doc.BindingType;
import org.sakaiproject.kernel.api.doc.ServiceBinding;
import org.sakaiproject.kernel.api.doc.ServiceDocumentation;
import org.sakaiproject.kernel.api.doc.ServiceExtension;
import org.sakaiproject.kernel.api.doc.ServiceMethod;
import org.sakaiproject.kernel.api.doc.ServiceParameter;
import org.sakaiproject.kernel.api.doc.ServiceResponse;
import org.sakaiproject.kernel.api.doc.ServiceSelector;
import org.sakaiproject.kernel.api.user.UserConstants;
import org.sakaiproject.kernel.connections.ConnectionUtils;
import org.sakaiproject.kernel.resource.AbstractVirtualPathServlet;
import org.sakaiproject.kernel.resource.VirtualResourceProvider;
import org.sakaiproject.kernel.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

/**
 * Servlet interface to the personal connections/contacts service.
 */
@ServiceDocumentation(name="Personal Connection Servlet",
  description="Manage personal connections and contacts. " +
    "Maps to node of resourceType sakai/contactstore at the URL /_user/contacts. " +
    "Each new contact results in two new nodes of resourceType sakai/contact, one for the inviting user and one for the invited user. "+
    "These contacts can be retrieved by GET requests which specify a connection-status: "+
    "/_user/contacts/accepted.json, /_user/contacts/pending.json, /_user/contacts/all.json, etc.",
  shortDescription="Manage personal connections/contacts",
  bindings=@ServiceBinding(type=BindingType.PATH,bindings="/_user/contacts/OTHER_USER",
      selectors={
      @ServiceSelector(name="invite",description="Invite the other user to connect"),
      @ServiceSelector(name="accept",description="Accept the invitation from the other user"),
      @ServiceSelector(name="reject",description="Refuse the invitation from the other user"),
      @ServiceSelector(name="ignore",description="Ignore the invitation from the other user"),
      @ServiceSelector(name="block",description="Ignore this and any future invitations from the other user"),
      @ServiceSelector(name="remove",description="Remove the invitation or connection, allowing future connections"),
      @ServiceSelector(name="cancel",description="Cancel the pending invitation to the other user")
  },
  extensions={
    @ServiceExtension(name="html", description="All POST operations produce HTML")
  }),
  methods=@ServiceMethod(name="POST",
    description={"Manage a personal contact (a connection with another user), specifying an operation as a selector. ",
      "Examples:<br>" +
      "<pre>curl -u from_user:fromPwd -F toRelationships=Supervisor -F fromRelationships=Supervised " +
      "http://localhost:8080/_user/contacts/to_user.invite.html</pre>" +
      "<pre>curl -X POST -u to_user:toPwd http://localhost:8080/_user/contacts/from_user.accept.html</pre>"
      },
    parameters={
      @ServiceParameter(name="toRelationships", description="The type of connection from the inviting user's point of view (only for invite)"),
      @ServiceParameter(name="fromRelationships", description="The type of connection from the invited user's point of view (only for invite)"),
      @ServiceParameter(name="sakai:types", description="Relationship types without regard to point-of-view " +
          "(affects the current user's view of the connection on any POST; affects the other user's view only for invite)"),
      @ServiceParameter(name="",description="Additional parameters become connection node properties (optional)")
    },
    response={
      @ServiceResponse(code=200,description="Success."),
      @ServiceResponse(code=400,description="Failure due to illegal operation request."),
      @ServiceResponse(code=404,description="Failure due to unknown user.")
    }
  )
)
@SlingServlet(resourceTypes="sakai/contactstore",methods={"GET","POST","PUT","DELETE"}, 
    selectors={"invite", "accept", "reject", "ignore", "block", "remove", "cancel"})
@Properties(value = {
    @Property(name = "service.description", value = "Provides support for connection stores."),
    @Property(name = "service.vendor", value = "The Sakai Foundation") })
public class ConnectionServlet extends AbstractVirtualPathServlet {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ConnectionServlet.class);
  private static final long serialVersionUID = 1112996718559864951L;

  private static final String TARGET_USERID = "connections:targetUserId";

  @Reference
  protected ConnectionManager connectionManager;

  @Reference
  protected VirtualResourceProvider virtualResourceProvider;

  protected void bindConnectionManager(ConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  protected void unbindConnectionManager(ConnectionManager connectionManager) {
    this.connectionManager = null;
  }

  @Override
  protected String getTargetPath(Resource baseResource, SlingHttpServletRequest request,
      SlingHttpServletResponse response, String realPath, String virtualPath) {
    String path;
    String user = request.getRemoteUser(); // current user
    if (user == null || UserConstants.ANON_USERID.equals(user)) {
      // cannot proceed if the user is not logged in
      try {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
            "User must be logged in to access connections");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      path = realPath; // default
    } else {
      
      // /_user/contacts.invite.html
      // /_user/contacts/aaron.accept.html
      
      String[] virtualParts = StringUtils.split(virtualPath, '.');
      if (virtualParts.length > 0) {
        String targetUser = virtualParts[0];
        path = ConnectionUtils.getConnectionPath(user,targetUser,virtualPath);
        request.setAttribute(TARGET_USERID, targetUser);
      } else {
        // nothing extra included so use the base
        path = realPath;
      }
    }
    return path;
  }


  /**
   * {@inheritDoc}
   * 
   * @throws IOException
   * 
   * @see org.sakaiproject.kernel.resource.AbstractVirtualPathServlet#preDispatch(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse,
   *      org.apache.sling.api.resource.Resource, org.apache.sling.api.resource.Resource)
   */
  @SuppressWarnings("unchecked")
  @Override
  protected boolean preDispatch(SlingHttpServletRequest request,
      SlingHttpServletResponse response, Resource baseResource, Resource resource)
      throws IOException {
    ConnectionOperation operation = ConnectionOperation.noop;
    if ("POST".equals(request.getMethod())) {
      String selector = request.getRequestPathInfo().getSelectorString();
      try {
        operation = ConnectionOperation.valueOf(selector);
      } catch (IllegalArgumentException e) {
        operation = ConnectionOperation.noop;
      }
    }
    try {
      String user = request.getRemoteUser(); // current user
      String targetUserId = null;
      switch (operation) {
      case noop:
        return true;
      default: 
        targetUserId = (String) request.getAttribute(TARGET_USERID);
        if (targetUserId == null || "".equals(targetUserId)) {
          response
              .sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                  "targetUserId not found in the request, cannot continue without it being set");
          return false;
        }
      }
      LOGGER.info("Connection {} {} {} ",new Object[]{user,targetUserId,operation});
      return connectionManager.connect(request.getParameterMap(), baseResource, user, targetUserId, operation);
    } catch (ConnectionException e) {
      LOGGER.error("Connection exception: {}", e);
      response.sendError(e.getCode(), e.getMessage());
      return false;
    }
  }


  /**
   * {@inheritDoc}
   * @see org.sakaiproject.kernel.resource.AbstractVirtualPathServlet#getVirtualResourceProvider()
   */
  @Override
  protected VirtualResourceProvider getVirtualResourceProvider() {
    return virtualResourceProvider;
  }


}
