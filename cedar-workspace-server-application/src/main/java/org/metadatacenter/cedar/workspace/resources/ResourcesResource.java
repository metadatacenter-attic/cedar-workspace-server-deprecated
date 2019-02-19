package org.metadatacenter.cedar.workspace.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.basic.FolderServerResource;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerNodeCurrentUserReport;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerResourceCurrentUserReport;
import org.metadatacenter.model.folderserver.extract.FolderServerNodeExtract;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.util.http.CedarResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.model.ModelNodeNames.BIBO_STATUS;
import static org.metadatacenter.model.ModelNodeNames.PAV_VERSION;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.rest.assertion.GenericAssertions.NonEmpty;

@Path("/resources")
@Produces(MediaType.APPLICATION_JSON)
public class ResourcesResource extends AbstractFolderServerResource {

  private static final Logger log = LoggerFactory.getLogger(ResourcesResource.class);

  public ResourcesResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findResource(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    }

    folderSession.addPathAndParentId(resource);

    List<FolderServerNodeExtract> pathInfo = folderSession.findNodePathExtract(resource);
    resource.setPathInfo(pathInfo);

    return Response.ok().entity(resource).build();

  }

  @PUT
  @Timed
  @Path("/{id}")
  public Response updateResource(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    c.must(c.request().getRequestBody()).be(NonEmpty);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    CedarParameter name = c.request().getRequestBody().get("name");
    String nameV = null;
    if (!name.isEmpty()) {
      nameV = name.stringValue();
      nameV = nameV.trim();
    }

    CedarParameter description = c.request().getRequestBody().get("description");
    String descriptionV = null;
    if (!description.isEmpty()) {
      descriptionV = description.stringValue();
      descriptionV = descriptionV.trim();
    }

    CedarParameter identifier = c.request().getRequestBody().get("identifier");
    String identifierV = null;
    if (!identifier.isEmpty()) {
      identifierV = identifier.stringValue();
      identifierV = identifierV.trim();
    }

    CedarParameter newVersionParam = c.request().getRequestBody().get("version");
    ResourceVersion newVersion = null;
    if (!newVersionParam.isEmpty()) {
      newVersion = ResourceVersion.forValueWithValidation(newVersionParam.stringValue());
    }
    if (!newVersionParam.isEmpty() && !newVersion.isValid()) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_DATA)
          .parameter("version", newVersionParam.stringValue())
          .build();
    }

    CedarParameter newPublicationStatusParam = c.request().getRequestBody().get("publicationStatus");
    BiboStatus newPublicationStatus = null;
    if (!newPublicationStatusParam.isEmpty()) {
      newPublicationStatus = BiboStatus.forValue(newPublicationStatusParam.stringValue());
    }
    if (!newPublicationStatusParam.isEmpty() && newPublicationStatus == null) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_DATA)
          .parameter("publicationStatus", newPublicationStatusParam.stringValue())
          .build();
    }

    if ((name == null || name.isEmpty()) && (description == null || description.isEmpty()) &&
        (newVersionParam == null || newVersionParam.isEmpty()) && (newPublicationStatusParam == null ||
        newPublicationStatusParam.isEmpty()
    )) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.MISSING_DATA)
          .errorMessage("No known data was supplied to the request! Possible fields are: name, description, " +
              PAV_VERSION + ", " + BIBO_STATUS)
          .build();
    }

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    } else {
      Map<NodeProperty, String> updateFields = new HashMap<>();
      if (description != null && !description.isEmpty()) {
        updateFields.put(NodeProperty.DESCRIPTION, descriptionV);
      }
      if (name != null && !name.isEmpty()) {
        updateFields.put(NodeProperty.NAME, nameV);
      }
      if (identifier != null && !identifier.isEmpty()) {
        updateFields.put(NodeProperty.IDENTIFIER, identifierV);
      }
      if (newVersion != null && newVersion.isValid()) {
        updateFields.put(NodeProperty.VERSION, newVersion.getValue());
      }
      if (newPublicationStatus != null) {
        updateFields.put(NodeProperty.PUBLICATION_STATUS, newPublicationStatus.getValue());
      }
      FolderServerResource updatedResource = folderSession.updateResourceById(id, resource.getType(), updateFields);
      if (updatedResource == null) {
        return CedarResponse.internalServerError().build();
      } else {
        return Response.ok().entity(updatedResource).build();
      }
    }
  }

  @GET
  @Timed
  @Path("/{id}/current-user-report")
  public Response getCurrentUserReport(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    PermissionServiceSession permissionServiceSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    }

    folderSession.addPathAndParentId(resource);

    List<FolderServerNodeExtract> pathInfo = folderSession.findNodePathExtract(resource);
    resource.setPathInfo(pathInfo);

    FolderServerResourceCurrentUserReport resourceReport =
        (FolderServerResourceCurrentUserReport) FolderServerNodeCurrentUserReport.fromNode(resource);

    decorateResourceWithCurrentUserPermissions(c, resourceReport);

    return Response.ok().entity(resourceReport).build();
  }

}
