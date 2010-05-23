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
package org.sakaiproject.kernel.doc;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.kernel.api.doc.BindingType;
import org.sakaiproject.kernel.api.doc.ServiceBinding;
import org.sakaiproject.kernel.api.doc.ServiceDocumentation;
import org.sakaiproject.kernel.api.doc.ServiceMethod;
import org.sakaiproject.kernel.api.doc.ServiceParameter;
import org.sakaiproject.kernel.api.doc.ServiceResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

/**
 * Creates documentation by tracking servlets and inspecting some annotations.
 */
@SlingServlet(methods = "GET", paths = "/system/doc")
@ServiceDocumentation(name = "DocumentationServlet", 
    description = "Provides auto documentation of servlets registered with OSGi. Documentation will use the "
    + "service registration properties, or annotations if present."
    + " Requests to this servlet take the form /system/doc?p=&lt;classname&gt where <em>classname</em>"
    + " is the fully qualified name of the class deployed into the OSGi container. If the class is "
    + "not present a 404 will be retruned, if the class is present, it will be interogated to extract "
    + "documentation from the class. In addition to extracting annotation based documention the servlet will "
    + "display the OSGi service properties. All documentation is assumed to be HTML encoded. If the browser is "
    + "directed to <a href=\"/system/doc\" >/system/doc</a> a list of all servlets in the system will be displayed ",
    shortDescription="Documentation servlet that renders service documentation ",
    bindings = @ServiceBinding(type = BindingType.PATH, bindings = "/system/doc"), 
    methods = { 
         @ServiceMethod(name = "GET", 
             description = "GETs to this servlet will produce documentation for the class, " +
             		"or an index of all servlets.", 
             parameters = @ServiceParameter(name = "p", 
                 description = "The name of the class to display the documentation for"),
              response= {@ServiceResponse(code=200,description="html page for the requested resource"),
           @ServiceResponse(code=404,description="Servlet class not found")}) })
public class DocumentationServlet extends SlingSafeMethodsServlet {

  /**
   * 
   */
  private static final long serialVersionUID = -6622263132868029827L;
  public static final String PREFIX = "/system/doc";
  private static final CharSequence HTML_HEADER = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 TRANSITIONAL//EN\">"
      + "<html><head>"
      + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">"
      + "<link rel=\"stylesheet\" type=\"text/css\" href=\"/sling.css\" >"
      + "<link rel=\"stylesheet\" type=\"text/css\" href=\""
      + PREFIX
      + "?p=style\">"
      + "</head><body>";
  private static final CharSequence HTML_FOOTER = "</body></html>";
  private ServletDocumentationTracker servletTracker;
  private byte[] style;

  /**
   * {@inheritDoc}
   * 
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    RequestParameter p = request.getRequestParameter("p");
    if (p == null) {
      sendIndex(response);
    } else if ("style".equals(p.getString())) {
      if (style == null) {
        System.err.println("Loading Style");
        InputStream in = this.getClass().getResourceAsStream("style.css");
        style = IOUtils.toByteArray(in);
        in.close();
      }
      System.err.println("Sending " + style.length);
      response.setContentType("text/css; charset=UTF-8");
      response.setContentLength(style.length);
      response.getOutputStream().write(style);
    } else {

      ServletDocumentation doc = servletTracker.getServletDocumentation().get(
          p.getString());
      if (doc == null) {
        response.setBufferSize(404);
        return;
      }
      send(request, response, doc);
    }
    return;
  }

  public void activate(ComponentContext context) {
    BundleContext bundleContext = context.getBundleContext();
    servletTracker = new ServletDocumentationTracker(bundleContext);
    servletTracker.open();
  }

  public void deactivate() {
    if (servletTracker != null) {
      servletTracker.close();
      servletTracker = null;
    }
  }

  /**
   * @param response
   * @throws IOException
   */
  private void sendIndex(SlingHttpServletResponse response) throws IOException {
    PrintWriter writer = response.getWriter();
    writer.append(HTML_HEADER);
    writer.append("<h1>List of Services</h1>");
    writer.append("<ul>");
    Map<String, ServletDocumentation> m = servletTracker.getServletDocumentation();
    List<ServletDocumentation> o = new ArrayList<ServletDocumentation>(m.values());
    Collections.sort(o);
    for (ServletDocumentation k : o) {
      String key = k.getKey();
      if ( key != null ) {
        writer.append("<li><a href=\"");
        writer.append(PREFIX);
        writer.append("?p=");
        writer.append(k.getKey());
        writer.append("\">");
        writer.append(k.getName());
        writer.append("</a><p>");
        writer.append(k.getShortDescription());
        writer.append("</p></li>");
      }
    }
    writer.append("</ul>");
    writer.append(HTML_FOOTER);
  }

  /**
   * @param request
   * @param response
   * @param doc
   * @throws IOException
   */
  private void send(SlingHttpServletRequest request, SlingHttpServletResponse response,
      ServletDocumentation doc) throws IOException {
    PrintWriter writer = response.getWriter();
    writer.append(HTML_HEADER);
    writer.append("<h1>Service: ");
    writer.append(doc.getName());
    writer.append("</h1>");
    doc.send(request, response);
    writer.append(HTML_FOOTER);
  }

}
