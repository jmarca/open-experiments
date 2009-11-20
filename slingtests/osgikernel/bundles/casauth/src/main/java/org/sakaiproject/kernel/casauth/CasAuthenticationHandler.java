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
package org.sakaiproject.kernel.casauth;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.engine.auth.AuthenticationHandler;
import org.apache.sling.engine.auth.AuthenticationInfo;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.jackrabbit.server.security.AuthenticationPlugin;
import org.apache.sling.jcr.jackrabbit.server.security.LoginModulePlugin;
import org.jasig.cas.client.authentication.DefaultGatewayResolverImpl;
import org.jasig.cas.client.authentication.GatewayResolver;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.jasig.cas.client.validation.TicketValidationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Principal;
import java.util.Dictionary;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component(immediate = false, label = "%auth.cas.name", description = "%auth.cas.description", enabled = false, metatype = true)
@Service
public final class CasAuthenticationHandler implements AuthenticationHandler,
    LoginModulePlugin {

  @Property(value = "https://localhost:8443")
  protected static final String serverName = "auth.cas.server.name";

  @Property(value = "https://localhost:8443/cas/login")
  protected static final String loginUrl = "auth.cas.server.login";

  /**
   * Path on which this authentication should be activated.
   */
  @Property(value = "/")
  static final String PATH_PROPERTY = AuthenticationHandler.PATH_PROPERTY;

  @Reference
  private SlingRepository repository;

  /** Defines the parameter to look for for the service. */
  private String serviceParameterName = "service";

  private static final Logger LOGGER = LoggerFactory
      .getLogger(CasAuthenticationHandler.class);

  /** Represents the constant for where the assertion will be located in memory. */
  public static final String CONST_CAS_ASSERTION = "_const_cas_assertion_";

  /** Defines the parameter to look for for the artifact. */
  private String artifactParameterName = "ticket";

  private boolean renew = false;

  private GatewayResolver gatewayStorage = new DefaultGatewayResolverImpl();

  private String casServerUrl = null;

  private String casServerLoginUrl = null;

  public static final String AUTH_TYPE = CasAuthenticationHandler.class.getName();

  public AuthenticationInfo authenticate(HttpServletRequest request,
      HttpServletResponse response) {
    LOGGER.debug("authenticate called");
    AuthenticationInfo authnInfo = null;
    // See if we already have auth info on the request
    final HttpSession session = request.getSession(false);
    final Assertion assertion = session != null ? (Assertion) session
        .getAttribute(CONST_CAS_ASSERTION) : null;
    if (assertion != null) {
      LOGGER.debug("assertion found");
      authnInfo = createAuthnInfo(assertion);
      // See if the user requested forced auth
    } else if (isForcedAuth(request)) {
      try {
        redirectToCas(request, response);
        authnInfo = AuthenticationInfo.DOING_AUTH;
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
      }
      // See if the user is already authenticated through CAS
    } else {
      final String serviceUrl = constructServiceUrl(request, response);
      final String ticket = CommonUtils.safeGetParameter(request, artifactParameterName);
      final boolean wasGatewayed = this.gatewayStorage.hasGatewayedAlready(request,
          serviceUrl);

      if (CommonUtils.isNotBlank(ticket) || wasGatewayed) {
        LOGGER.debug("found ticket: \"{}\" or was gatewayed", ticket);
        authnInfo = getUserFromTicket(ticket, serviceUrl, request);
      } else {
        LOGGER.debug("no ticket and no assertion found");
      }
    }
    return authnInfo;
  }

  private boolean isForcedAuth(HttpServletRequest request) {
    return (request.getParameter("sling:authRequestLogin") != null);
  }

  @SuppressWarnings("unchecked")
  private AuthenticationInfo createAuthnInfo(final Assertion assertion) {
    AuthenticationInfo authnInfo;
    SimpleCredentials creds = new SimpleCredentials(assertion.getPrincipal().getName(),
        new char[0]);
    Map<String, String> attribs = assertion.getAttributes();
    for (Entry<String, String> e : attribs.entrySet()) {
      creds.setAttribute(e.getKey(), e.getValue());
    }
    authnInfo = new AuthenticationInfo(AUTH_TYPE, creds);
    return authnInfo;
  }

  public boolean requestAuthentication(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    LOGGER.debug("requestAuthentication called");
    redirectToCas(request, response);
    return true;
  }

  private void redirectToCas(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    final String serviceUrl = constructServiceUrl(request, response);
    final String modifiedServiceUrl;

    Boolean gateway = Boolean.parseBoolean(request.getParameter("gateway"));
    if (gateway) {
      LOGGER.debug("setting gateway attribute in session");
      modifiedServiceUrl = this.gatewayStorage.storeGatewayInformation(request,
          serviceUrl);
    } else {
      modifiedServiceUrl = serviceUrl;
    }

    final String urlToRedirectTo = CommonUtils.constructRedirectUrl(
        this.casServerLoginUrl, this.serviceParameterName, modifiedServiceUrl,
        this.renew, gateway);

    LOGGER.debug("Redirecting to: \"{}\"", urlToRedirectTo);
    response.sendRedirect(urlToRedirectTo);
  }

  private AuthenticationInfo getUserFromTicket(String ticket, String serviceUrl,
      HttpServletRequest request) {
    AuthenticationInfo authnInfo = null;
    Cas20ServiceTicketValidator sv = new Cas20ServiceTicketValidator(casServerUrl);
    try {
      Assertion a = sv.validate(ticket, serviceUrl);
      request.getSession().setAttribute(CONST_CAS_ASSERTION, a);
      authnInfo = createAuthnInfo(a);
    } catch (TicketValidationException e) {
      LOGGER.error(e.getMessage());
    }
    return authnInfo;
  }

  private String constructServiceUrl(HttpServletRequest request,
      HttpServletResponse response) {
    String serviceUrl = request.getRequestURL().toString();
    serviceUrl = response.encodeURL(serviceUrl);
    return serviceUrl;
  }

  @SuppressWarnings("unchecked")
  @Activate
  protected void activate(ComponentContext context) {
    Dictionary properties = context.getProperties();
    casServerUrl = (String) properties.get(serverName);
    casServerLoginUrl = (String) properties.get(loginUrl);
  }

  @SuppressWarnings("unchecked")
  public void addPrincipals(Set principals) {
    // Nothing to do

  }

  public boolean canHandle(Credentials credentials) {
    boolean result = (credentials instanceof SimpleCredentials);
    return result;
  }

  @SuppressWarnings("unchecked")
  public void doInit(CallbackHandler callbackHandler, Session session, Map options)
      throws LoginException {
    // Nothing to do
  }

  public AuthenticationPlugin getAuthentication(Principal principal, Credentials creds)
      throws RepositoryException {
    return new CasAuthentication(principal, repository);
  }

  public Principal getPrincipal(Credentials credentials) {
    CasPrincipal user = null;
    if (credentials != null && credentials instanceof SimpleCredentials) {
      SimpleCredentials sc = (SimpleCredentials) credentials;
      user = new CasPrincipal(sc.getUserID());
    }
    return user;
  }

  public int impersonate(Principal principal, Credentials credentials)
      throws RepositoryException, FailedLoginException {
    return LoginModulePlugin.IMPERSONATION_DEFAULT;
  }

  protected void bindRepository(SlingRepository repository) {
    this.repository = repository;
  }
}