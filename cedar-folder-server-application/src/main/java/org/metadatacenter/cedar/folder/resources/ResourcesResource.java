package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarBackendException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.FolderOrResource;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerResource;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsRequest;
import org.metadatacenter.server.security.model.auth.NodePermission;
import org.metadatacenter.util.CedarNodeTypeUtil;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.rest.assertion.GenericAssertions.NonEmpty;

@Path("/resources")
@Produces(MediaType.APPLICATION_JSON)
public class ResourcesResource extends AbstractFolderServerResource {

  public ResourcesResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Timed
  public Response createResource() throws CedarException {
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


    String descriptionV = null;
    CedarParameter description = c.request().getRequestBody().get("description");
    // let's not read resource description for instances
    if (nodeType != CedarNodeType.INSTANCE) {
      c.must(description).be(NonEmpty);
    }
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
      NodeLabel nodeLabel = NodeLabel.forCedarNodeType(nodeType);
      newResource = folderSession.createResourceAsChildOfId(parentId, id, nodeType, name
          .stringValue(), descriptionV, nodeLabel);
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
      if (permissionSession.userIsOwnerOfResource(id)) {
        resource.addCurrentUserPermission(NodePermission.CHANGEOWNER);
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

    if ((name == null || name.isEmpty()) && (description == null || description.isEmpty())) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.MISSING_NAME_AND_DESCRIPTION)
          .errorMessage("You must supply the new description or the new name of the resource!")
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
      Map<String, String> updateFields = new HashMap<>();
      if (description != null) {
        updateFields.put("description", descriptionV);
      }
      if (name != null) {
        updateFields.put("name", nameV);
        updateFields.put("displayName", nameV);
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
      e.printStackTrace();
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

}
