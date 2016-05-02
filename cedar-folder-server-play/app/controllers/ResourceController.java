package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSResource;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.util.ParameterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Result;
import utils.DataServices;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ResourceController extends AbstractFolderServerController {
  private static Logger log = LoggerFactory.getLogger(ResourceController.class);
  private static ObjectMapper MAPPER = new ObjectMapper();

  public static Result createResource() {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while creating the resource", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode creationRequest = request().body().asJson();
      if (creationRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(Authorization.getAccountInfo
          (frontendRequest));

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
      String resourceTypeString = ParameterUtil.getStringOrThrowError(creationRequest, "resourceType",
          "You must supply the resourceType of the new resource!");

      CedarNodeType resourceType = CedarNodeType.forValue(resourceTypeString);
      if (resourceType == null) {
        StringBuilder sb = new StringBuilder();
        Arrays.asList(CedarNodeType.values()).forEach(crt -> sb.append(crt.getValue()).append(","));
        throw new IllegalArgumentException("The supplied resource type is invalid! It should be one of:" + sb
            .toString());
      }


      String description = "";
      // let's not read resource description for instances
      if (resourceType != CedarNodeType.INSTANCE) {
        // get description
        description = ParameterUtil.getStringOrThrowError(creationRequest, "description",
            "You must supply the description of the new resource!");
      }

      // check existence of parent folder
      CedarFSResource newResource = null;
      CedarFSFolder parentFolder = neoSession.findFolderById(parentId);

      String candidatePath = null;
      if (parentFolder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("parentId", parentId);
        return badRequest(generateErrorDescription("parentNotPresent",
            "The parent folder is not present:" + parentId, errorParams));
      } else {
        // Later we will guarantee some kind of uniqueness for the resource names
        // Currently we allow duplicate names, the id is the PK
        newResource = neoSession.createResourceAsChildOfId(parentId, id, resourceType, name, description);
      }

      if (newResource != null) {
        JsonNode createdResource = MAPPER.valueToTree(newResource);
        String absoluteUrl = routes.ResourceController.findResource(id).absoluteURL(request());
        response().setHeader(HttpConstants.HTTP_HEADER_LOCATION, absoluteUrl);
        return created(createdResource);
      } else {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("parentId", parentId);
        errorParams.put("id", id);
        errorParams.put("resourceType", resourceTypeString);
        return badRequest(generateErrorDescription("resourceNotCreated",
            "The resource was not created:" + id, errorParams));
      }
    } catch (IllegalArgumentException e) {
      play.Logger.error("Illegal argument while creating the resource", e);
      return badRequestWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while creating the resource", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result findResource(String resourceId) {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while reading the resource", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(Authorization.getAccountInfo
          (frontendRequest));

      CedarFSResource resource = neoSession.findResourceById(resourceId);
      if (resource == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", resourceId);
        return notFound(generateErrorDescription("resourceNotFound",
            "The resource can not be found by id:" + resourceId, errorParams));
      } else {
        neoSession.addPathAndParentId(resource);
        JsonNode folderNode = MAPPER.valueToTree(resource);
        return ok(folderNode);
      }
    } catch (Exception e) {
      play.Logger.error("Error while getting the resource", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result updateResource(String resourceId) {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while updating the resource", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode folderUpdateRequest = request().body().asJson();
      if (folderUpdateRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(Authorization.getAccountInfo
          (frontendRequest));

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

      CedarFSResource resource = neoSession.findResourceById(resourceId);
      if (resource == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", resourceId);
        return notFound(generateErrorDescription("resourceNotFound",
            "The resource can not be found by id:" + resourceId, errorParams));
      } else {
        Map<String, String> updateFields = new HashMap<>();
        if (description != null) {
          updateFields.put("description", description);
        }
        if (name != null) {
          updateFields.put("name", name);
        }
        //TODO: fix this
        CedarFSResource updatedFolder = neoSession.updateResourceById(resourceId, CedarNodeType.ELEMENT, updateFields);
        if (updatedFolder == null) {
          return notFound();
        } else {
          JsonNode updatedFolderNode = MAPPER.valueToTree(updatedFolder);
          return ok(updatedFolderNode);
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the resource", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result deleteResource(String resourceId) {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while deleting the resource", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(Authorization.getAccountInfo
          (frontendRequest));

      CedarFSResource resource = neoSession.findResourceById(resourceId);
      if (resource == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", resourceId);
        return notFound(generateErrorDescription("resourceNotFound",
            "The resource can not be found by id:" + resourceId, errorParams));
      } else {
        boolean deleted = neoSession.deleteResourceById(resourceId, CedarNodeType.ELEMENT);
        if (deleted) {
          return noContent();
        } else {
          ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
          errorParams.put("id", resourceId);
          return internalServerErrorWithError(generateErrorDescription("resourceNotDeleted",
              "The resource can not be delete by id:" + resourceId, errorParams));
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the resource", e);
      return internalServerErrorWithError(e);
    }
  }

}
