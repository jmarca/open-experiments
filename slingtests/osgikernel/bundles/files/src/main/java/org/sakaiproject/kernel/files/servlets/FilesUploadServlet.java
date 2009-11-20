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
package org.sakaiproject.kernel.files.servlets;

import com.google.common.collect.Lists;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.jcr.api.SlingRepository;
import org.sakaiproject.kernel.api.cluster.ClusterTrackingService;
import org.sakaiproject.kernel.api.doc.BindingType;
import org.sakaiproject.kernel.api.doc.ServiceBinding;
import org.sakaiproject.kernel.api.doc.ServiceDocumentation;
import org.sakaiproject.kernel.api.doc.ServiceMethod;
import org.sakaiproject.kernel.api.doc.ServiceParameter;
import org.sakaiproject.kernel.api.doc.ServiceResponse;
import org.sakaiproject.kernel.api.doc.ServiceSelector;
import org.sakaiproject.kernel.api.files.FileUtils;
import org.sakaiproject.kernel.api.files.FilesConstants;
import org.sakaiproject.kernel.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Create a file
 * 
 */
@SlingServlet(resourceTypes = { "sakai/files" }, methods = { "POST" }, selectors = { "upload" })
@Properties(value = {
    @Property(name = "service.description", value = "Servlet to allow uploading of files to the store."),
    @Property(name = "service.vendor", value = "The Sakai Foundation") })
@ServiceDocumentation(
    name = "FilesUploadServlet", 
    shortDescription = "Upload a file in the repository.",
    bindings = @ServiceBinding(
        type = BindingType.TYPE,
        selectors = @ServiceSelector(name="upload", description = "Upload one or more files."),
        bindings = "sakai/files"
    ),
    methods = {@ServiceMethod(
        name = "POST", 
        description = "Upload one or more files to the repository. " +
    		"By default there is a filestore at /_user/files",
    		parameters = {
            @ServiceParameter(
                name="Filedata", 
                description="Required: the parameter that holds the actual data for the file that should be uploaded. This can be multivalued."),
            @ServiceParameter(
                name="link", 
                description="Optional: absolute path where you want to create a link for the uploaded file(s).<br />" +
            		"If this is a multiple file upload then there will be a link for each file at this directory.<br />" +
            		"The link will have the same name as the uploaded file."),
            @ServiceParameter(
                name="site", 
                description="Optional: the absolute path to a site that should be associated with this file.")
        },
        response = {
            @ServiceResponse(
                code = 200, 
                description = "Everything went OK and all the files (and associated links) were created.<br />" +
                		"The body will also contain a JSON response holding 2 keys. 'files' and 'links'.<br />" +
                		"Each is an array with the respective properties for both."),
        		@ServiceResponse(
                code = 400,
                description = "Filedata parameter was not provided."
            ),
            @ServiceResponse(
                code = 500,
                description = "Failure with HTML explanation."
            )
        }
    )}
)
public class FilesUploadServlet extends SlingAllMethodsServlet {

  public static final Logger LOG = LoggerFactory.getLogger(FilesUploadServlet.class);
  private static final long serialVersionUID = -2582970789079249113L;

  @Reference
  private ClusterTrackingService clusterTrackingService;

  @Reference
  private SlingRepository slingRepository;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    Session session = request.getResourceResolver().adaptTo(Session.class);
    String store = request.getResource().getPath();
    LOG.info("Attempted upload for " + session.getUserID() + " - " + request.getRemoteUser());

    // If there is a link parameter provided than we will create a
    // link for each file under this path.
    RequestParameter linkParam = request.getRequestParameter("link");
    if (linkParam != null) {
      String link = linkParam.getString();
      if (!link.startsWith("/")) {
        response
            .sendError(500,
                "If a link location is specified, it should be absolute and point to a folder.");
        return;
      }
    }

    RequestParameter siteParam = request.getRequestParameter("site");
    if (siteParam != null) {
      String site = siteParam.getString();
      if (!site.startsWith("/")) {
        response.sendError(500,
            "If a site is specified, it should be absolute and point to a site.");
        return;
      }
    }

    List<Node> fileNodes = Lists.newArrayList();
    List<String> links = Lists.newArrayList();

    // Create the files and links.
    try {
      // Handle multi files
      RequestParameter[] files = request.getRequestParameters("Filedata");
      if (files == null) {
        response.sendError(400, "Missing Filedata parameter.");
        return;
      }

      // Loop over each file parameter request and create a file.
      for (RequestParameter file : files) {
        Node fileNode = createFile(session, store, file);
        fileNodes.add(fileNode);
      }

      // Create a link for each file if there is a need for it.
      if (linkParam != null && siteParam != null) {
        Node linkFolder = (Node) session.getItem(linkParam.getString());
        // For each file .. create a link
        for (Node fileNode : fileNodes) {
          String fileName = fileNode.getProperty(FilesConstants.SAKAI_FILENAME)
              .getString();

          String linkPath = linkFolder.getPath() + "/" + fileName;
          String sitePath = siteParam.getString();
          FileUtils.createLink(session, fileNode, linkPath, sitePath, slingRepository);
          links.add(linkPath);
        }
      }

      // Send a response back to the user.

      ExtendedJSONWriter writer = new ExtendedJSONWriter(response.getWriter());
      writer.object();
      writer.key("files");
      writer.array();
      for (Node fileNode : fileNodes) {
        writer.object();
        writer.key("filename");
        writer.value(fileNode.getProperty(FilesConstants.SAKAI_FILENAME).getString());
        writer.key("path");
        writer.value(FileUtils.getDownloadPath(fileNode));
        writer.key("id");
        writer.value(fileNode.getProperty(FilesConstants.SAKAI_ID).getString());
        writer.endObject();
      }
      writer.endArray();
      if (links.size() > 0) {
        writer.key("links");
        writer.array();
        for (String link : links) {
          writer.value(link);
        }
        writer.endArray();
      }
      writer.endObject();

      // We send a 200 because SWFUpload has some problems dealing with other status
      // codes.
      response.setStatus(HttpServletResponse.SC_OK);

    } catch (RepositoryException e) {
      LOG.warn("Failed to create file.");
      e.printStackTrace();
      response.sendError(500, "Failed to save file.");
    } catch (JSONException e) {
      LOG.warn("Failed to write JSON format.");
      response.sendError(500, "Failed to write JSON format.");
      e.printStackTrace();
    }

  }

  /**
   * Creates a file under the store. Ex: store/aa/bb/cc/dd/myID
   * 
   * @param session
   * @param file
   * @param writer
   * @throws RepositoryException
   * @throws IOException
   * @throws JSONException
   */
  private Node createFile(Session session, String store, RequestParameter file)
      throws RepositoryException, IOException, JSONException {
    String contentType = file.getContentType();
    // Try to determine the real content type.
    // get content type
    if (contentType != null) {
      int idx = contentType.indexOf(';');
      if (idx > 0) {
        contentType = contentType.substring(0, idx);
      }
    }
    if (contentType == null || contentType.equals("application/octet-stream")) {
      ServletContext context = this.getServletConfig().getServletContext();
      contentType = context.getMimeType(file.getFileName());
      if (contentType == null || contentType.equals("application/octet-stream")) {
        contentType = "application/octet-stream";
      }
    }
    String id = clusterTrackingService.getClusterUniqueId();
    if (id.endsWith("=="))
      id = id.substring(0, id.length() - 2);

    id = id.replace('/', '_').replace('=', '-');

    String path = FileUtils.getHashedPath(store, id);

    Node fileNode = FileUtils.saveFile(session, path, id, file, contentType,
        slingRepository);
    return fileNode;
  }

}
