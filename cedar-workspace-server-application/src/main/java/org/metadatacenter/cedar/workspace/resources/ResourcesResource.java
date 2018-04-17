package org.metadatacenter.cedar.workspace.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarBackendException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.FolderOrResource;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerResource;
import org.metadatacenter.model.folderserver.FolderServerResourceBuilder;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsRequest;
import org.metadatacenter.server.security.model.auth.NodePermission;
import org.metadatacenter.util.CedarNodeTypeUtil;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.HashMap;
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

  @POST
  @Timed
  public Response createResource() throws CedarException {
    //TODO: use constants here, instead of strings. Also replace in ResourceServer code
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);

    c.must(c.user()).be(LoggedIn);

    c.must(c.request().getRequestBody()).be(NonEmpty);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    CedarParameter parentIdP = c.request().getRequestBody().get("parentId");
    c.must(parentIdP).be(NonEmpty);
    String parentId = parentIdP.stringValue();

    CedarParameter idP = c.request().getRequestBody().get("id");
    c.must(idP).be(NonEmpty);
    String id = idP.stringValue();

    CedarParameter name = c.request().getRequestBody().get("name");
    c.must(name).be(NonEmpty);

    CedarParameter nodeTypeP = c.request().getRequestBody().get("nodeType");
    c.must(nodeTypeP).be(NonEmpty);

    String nodeTypeString = nodeTypeP.stringValue();

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);
    if (!CedarNodeTypeUtil.isValidForRestCall(nodeType)) {
      return CedarResponse.badRequest()
          .errorMessage("You passed an illegal nodeType:'" + nodeTypeString +
              "'. The allowed values are:" + CedarNodeTypeUtil.getValidNodeTypesForRestCalls())
          .errorKey(CedarErrorKey.INVALID_NODE_TYPE)
          .parameter("invalidNodeTypes", nodeTypeString)
          .parameter("allowedNodeTypes", CedarNodeTypeUtil.getValidNodeTypeValuesForRestCalls())
          .build();
    }

    CedarParameter versionP = c.request().getRequestBody().get("version");

    CedarParameter publicationStatusP = c.request().getRequestBody().get("publicationStatus");


    if (nodeType != CedarNodeType.INSTANCE) {
      c.must(versionP).be(NonEmpty);
      c.must(publicationStatusP).be(NonEmpty);
    }

    String versionString = versionP.stringValue();
    ResourceVersion version = ResourceVersion.forValue(versionString);

    String publicationStatusString = publicationStatusP.stringValue();
    BiboStatus publicationStatus = BiboStatus.forValue(publicationStatusString);

    String descriptionV = null;
    CedarParameter description = c.request().getRequestBody().get("description");
    descriptionV = description.stringValue();

    // check existence of parent folder
    FolderServerResource newResource = null;
    FolderServerFolder parentFolder = folderSession.findFolderById(parentId);

    String candidatePath = null;
    if (parentFolder == null) {
      return CedarResponse.badRequest()
          .parameter("folderId", parentId)
          .errorKey(CedarErrorKey.PARENT_FOLDER_NOT_FOUND)
          .errorMessage("The parent folder is not present!")
          .build();
    } else {
      // Later we will guarantee some kind of uniqueness for the resource names
      // Currently we allow duplicate names, the id is the PK
      FolderServerResource brandNewResource = FolderServerResourceBuilder.forNodeType(nodeType);
      brandNewResource.setId1(id);
      brandNewResource.setType(nodeType);
      brandNewResource.setName1(name.stringValue());
      brandNewResource.setDescription1(descriptionV);
      brandNewResource.setVersion1(versionString);
      brandNewResource.setPublicationStatus1(publicationStatusString);
      if (nodeType.isVersioned()) {
        brandNewResource.setLatestVersion(true);
      }
      newResource = folderSession.createResourceAsChildOfId(brandNewResource, parentId);
    }

    if (newResource != null) {
      UriBuilder builder = uriInfo.getAbsolutePathBuilder();
      URI uri = builder.path(CedarUrlUtil.urlEncode(id)).build();
      return Response.created(uri).entity(newResource).build();
    } else {
      return CedarResponse.badRequest()
          .parameter("id", id)
          .parameter("parentId", parentId)
          .parameter("resourceType", nodeTypeString)
          .errorKey(CedarErrorKey.RESOURCE_NOT_CREATED)
          .errorMessage("The resource was not created!")
          .build();
    }
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findResource(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);
    VersionServiceSession versionSession = CedarDataServices.getVersionServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    } else {
      folderSession.addPathAndParentId(resource);
      if (permissionSession.userHasReadAccessToResource(id)) {
        resource.addCurrentUserPermission(NodePermission.READ);
      }
      if (permissionSession.userHasWriteAccessToResource(id)) {
        resource.addCurrentUserPermission(NodePermission.WRITE);
      }
      if (permissionSession.userCanChangeOwnerOfResource(id)) {
        resource.addCurrentUserPermission(NodePermission.CHANGEOWNER);
      }
      if (permissionSession.userHasWriteAccessToResource(id)) {
        resource.addCurrentUserPermission(NodePermission.CHANGEPERMISSIONS);
      }
      if (versionSession.userCanPerformVersioning(resource)) {
        if (versionSession.resourceCanBePublished(resource)) {
          resource.addCurrentUserPermission(NodePermission.PUBLISH);
        }
        if (versionSession.resourceCanBeDrafted(resource)) {
          resource.addCurrentUserPermission(NodePermission.CREATE_DRAFT);
        }
      }
      return Response.ok().entity(resource).build();
    }
  }

  @PUT
  @Timed
  @Path("/{id}")
  public Response updateResource(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
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
        (newVersionParam == null || newVersionParam.isEmpty()) && (newPublicationStatusParam == null || newPublicationStatusParam.isEmpty()
    )) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.MISSING_DATA)
          .errorMessage("No known data was supplied to the request! Known fields are: name, description, " +
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

  @DELETE
  @Timed
  @Path("/{id}")
  public Response deleteResource(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    } else {
      boolean deleted = folderSession.deleteResourceById(id, CedarNodeType.ELEMENT);
      if (deleted) {
        return Response.noContent().build();
      } else {
        return CedarResponse.internalServerError()
            .id(id)
            .errorKey(CedarErrorKey.RESOURCE_NOT_DELETED)
            .errorMessage("The resource can not be delete by id")
            .build();
      }
    }
  }

  @GET
  @Timed
  @Path("/{id}/permissions")
  public Response getPermissions(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    } else {
      CedarNodePermissions permissions = permissionSession.getNodePermissions(id, FolderOrResource.RESOURCE);
      return Response.ok().entity(permissions).build();
    }
  }

  @PUT
  @Timed
  @Path("/{id}/permissions")
  public Response updatePermissions(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    c.must(c.request().getRequestBody()).be(NonEmpty);
    JsonNode permissionUpdateRequest = c.request().getRequestBody().asJson();

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    CedarNodePermissionsRequest permissionsRequest = null;
    try {
      permissionsRequest = JsonMapper.MAPPER.treeToValue(permissionUpdateRequest, CedarNodePermissionsRequest.class);
    } catch (JsonProcessingException e) {
      log.error("Error while reading permission update request", e);
    }

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    } else {
      BackendCallResult backendCallResult = permissionSession.updateNodePermissions(id, permissionsRequest,
          FolderOrResource.RESOURCE);
      if (backendCallResult.isError()) {
        throw new CedarBackendException(backendCallResult);
      }
      CedarNodePermissions permissions = permissionSession.getNodePermissions(id, FolderOrResource.RESOURCE);
      return Response.ok().entity(permissions).build();
    }
  }

  @GET
  @Timed
  @Path("/{id}/report")
  public Response getReport(@PathParam(PP_ID) String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.RESOURCE_NOT_FOUND)
          .errorMessage("The resource can not be found by id")
          .build();
    } else {
      if (resource.getType() == CedarNodeType.INSTANCE) {
        //CedarNodePermissions permissions = permissionSession.getInstanceReport(id);
        return Response.ok().entity(resource).build();
      } else if (resource.getType() == CedarNodeType.ELEMENT) {
        return Response.ok().entity(resource).build();
      } else if (resource.getType() == CedarNodeType.TEMPLATE) {
        return Response.ok().entity(resource).build();
      }
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_DATA)
          .errorMessage("Invalid resource type")
          .parameter("nodeType", resource.getType().getValue())
          .build();
    }
  }


}
