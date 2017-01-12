package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarBackendException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsRequest;
import org.metadatacenter.server.security.model.auth.NodePermission;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.parameter.ParameterUtil;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

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

    JsonNode creationRequest = c.request().getRequestBody().asJson();
    if (creationRequest == null) {
      throw new IllegalArgumentException("You must supply the request body as a json object!");
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    // get parentId
    String parentId = ParameterUtil.getStringOrThrowError(creationRequest, "parentId",
        "You must supply the parentId of the new resource!");

    // get id
    String id = ParameterUtil.getStringOrThrowError(creationRequest, "id",
        "You must supply the id of the new resource!");

    // get name
    String name = ParameterUtil.getStringOrThrowError(creationRequest, "name",
        "You must supply the name of the new resource!");

    // get resourceType parameter
    String nodeTypeString = ParameterUtil.getStringOrThrowError(creationRequest, "nodeType",
        "You must supply the nodeType of the new resource!");

    CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);
    if (nodeType == null) {
      StringBuilder sb = new StringBuilder();
      Arrays.asList(CedarNodeType.values()).forEach(crt -> sb.append(crt.getValue()).append(","));
      throw new IllegalArgumentException("The supplied node type is invalid! It should be one of:" + sb
          .toString());
    }


    String description = "";
    // let's not read resource description for instances
    if (nodeType != CedarNodeType.INSTANCE) {
      // get description
      description = ParameterUtil.getStringOrThrowError(creationRequest, "description",
          "You must supply the description of the new resource!");
    }

    // check existence of parent folder
    FolderServerResource newResource = null;
    FolderServerFolder parentFolder = folderSession.findFolderById(parentId);

    String candidatePath = null;
    if (parentFolder == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("parentId", parentId);
      errorParams.put("errorId", "parentNotPresent");
      errorParams.put("errorMessage", "The parent folder is not present:" + parentId);
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    } else {
      // Later we will guarantee some kind of uniqueness for the resource names
      // Currently we allow duplicate names, the id is the PK
      NodeLabel nodeLabel = NodeLabel.forCedarNodeType(nodeType);
      newResource = folderSession.createResourceAsChildOfId(parentId, id, nodeType, name, description, nodeLabel);
    }

    if (newResource != null) {
      UriBuilder builder = uriInfo.getAbsolutePathBuilder();
      URI uri = builder.path(CedarUrlUtil.urlEncode(id)).build();
      return Response.created(uri).entity(newResource).build();
    } else {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("parentId", parentId);
      errorParams.put("id", id);
      errorParams.put("resourceType", nodeTypeString);
      errorParams.put("errorId", "resourceNotCreated");
      errorParams.put("errorMessage", "The resource was not created:" + id);
      return Response.status(Response.Status.BAD_REQUEST).entity(errorParams).build();
    }
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findResource(@PathParam("id") String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "resourceNotFound");
      errorParams.put("errorMessage", "The resource can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
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
  public Response updateResource(@PathParam("id") String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    JsonNode folderUpdateRequest = c.request().getRequestBody().asJson();
    if (folderUpdateRequest == null) {
      throw new IllegalArgumentException("You must supply the request body as a json object!");
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    String name = null;
    JsonNode nameNode = folderUpdateRequest.get("name");
    if (nameNode != null) {
      name = nameNode.asText();
      if (name != null) {
        name = name.trim();
      }
    }

    String description = null;
    JsonNode descriptionNode = folderUpdateRequest.get("description");
    if (descriptionNode != null) {
      description = descriptionNode.asText();
      if (description != null) {
        description = description.trim();
      }
    }

    if ((name == null || name.isEmpty()) && (description == null || description.isEmpty())) {
      throw new IllegalArgumentException("You must supply the new description or the new name of the resource!");
    }

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "resourceNotFound");
      errorParams.put("errorMessage", "The resource can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      Map<String, String> updateFields = new HashMap<>();
      if (description != null) {
        updateFields.put("description", description);
      }
      if (name != null) {
        updateFields.put("name", name);
        updateFields.put("displayName", name);
      }
      //TODO: fix this
      FolderServerResource updatedFolder = folderSession.updateResourceById(id, CedarNodeType.ELEMENT,
          updateFields);
      if (updatedFolder == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      } else {
        return Response.ok().entity(updatedFolder).build();
      }
    }
  }

  @DELETE
  @Timed
  @Path("/{id}")
  public Response deleteResource(@PathParam("id") String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "resourceNotFound");
      errorParams.put("errorMessage", "The resource can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      boolean deleted = folderSession.deleteResourceById(id, CedarNodeType.ELEMENT);
      if (deleted) {
        return Response.noContent().build();
      } else {
        Map<String, Object> errorParams = new HashMap<>();
        errorParams.put("id", id);
        errorParams.put("errorId", "resourceNotDeleted");
        errorParams.put("errorMessage", "The resource can not be delete by id:" + id);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorParams).build();
      }
    }
  }

  @GET
  @Timed
  @Path("/{id}/permissions")
  public Response getPermissions(@PathParam("id") String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

    FolderServerResource resource = folderSession.findResourceById(id);
    if (resource == null) {
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "resourceNotFound");
      errorParams.put("errorMessage", "The resource can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      CedarNodePermissions permissions = permissionSession.getNodePermissions(id, false);
      return Response.ok().entity(permissions).build();
    }
  }

  @PUT
  @Timed
  @Path("/{id}/permissions")
  public Response updatePermissions(@PathParam("id") String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    JsonNode permissionUpdateRequest = c.request().getRequestBody().asJson();
    if (permissionUpdateRequest == null) {
      throw new IllegalArgumentException("You must supply the request body as a json object!");
    }

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
      Map<String, Object> errorParams = new HashMap<>();
      errorParams.put("id", id);
      errorParams.put("errorId", "resourceNotFound");
      errorParams.put("errorMessage", "The resource can not be found by id:" + id);
      return Response.status(Response.Status.NOT_FOUND).entity(errorParams).build();
    } else {
      BackendCallResult backendCallResult = permissionSession.updateNodePermissions(id, permissionsRequest,
          false);
      if (backendCallResult.isError()) {
        throw new CedarBackendException(backendCallResult);
      }
      CedarNodePermissions permissions = permissionSession.getNodePermissions(id, false);
      return Response.ok().entity(permissions).build();
    }
  }

}
