package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.folderserver.CedarFolder;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.util.ParameterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Result;
import utils.DataServices;

import java.util.HashMap;
import java.util.Map;

public class FolderController extends AbstractFolderServerController {
  private static Logger log = LoggerFactory.getLogger(FolderController.class);

  public static Result createFolder() {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while creating the folder", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode folderCreationRequest = request().body().asJson();
      if (folderCreationRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      System.out.println(folderCreationRequest);

      // get path parameter
      String path = ParameterUtil.getStringOrThrowError(folderCreationRequest, "path",
          "You must supply the path of the new folder!");
      // test path syntax
      String normalizedPath = neo4JProxy.getPathUtil().normalizePath(path);
      if (!normalizedPath.equals(path)) {
        throw new IllegalArgumentException("You must supply the path of the new folder in normalized form!");
      }

      // get name parameter
      String name = ParameterUtil.getStringOrThrowError(folderCreationRequest, "name",
          "You must supply the name of the new folder!");
      // test new folder name syntax
      String normalizedName = neo4JProxy.getPathUtil().sanitizeName(name);
      if (!normalizedName.equals(name)) {
        throw new IllegalArgumentException("The new folder name contains invalid characters!");
      }

      // get description parameter
      String description = ParameterUtil.getStringOrThrowError(folderCreationRequest, "description",
          "You must supply the description of the new folder!");

      // check existence of parent folder
      CedarFolder newFolder = null;
      CedarFolder parentFolder = neo4JProxy.findFolderByPath(path);
      String candidatePath = null;
      if (parentFolder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("path", path);
        return badRequest(generateErrorDescription("parentNotPresent",
            "The parent folder is not present:" + path, errorParams));
      } else {
        candidatePath = neo4JProxy.getPathUtil().getChildPath(path, name);
        CedarFolder newFolderCandidate = neo4JProxy.findFolderByPath(candidatePath);
        if (newFolderCandidate != null) {
          ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
          errorParams.put("path", path);
          errorParams.put("name", name);
          errorParams.put("newFolderPath", candidatePath);
          return badRequest(generateErrorDescription("folderAlreadyPresent",
              "There is already a folder with the path:" + candidatePath, errorParams));
        }

        CedarUser cu = Authorization.getAccountInfo(frontendRequest);
        newFolder = neo4JProxy.createFolderAsChildOfId(parentFolder.getId(), name, description, cu.getUserId());
      }

      if (newFolder != null) {
        neo4JProxy.convertNeo4JValues(newFolder);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode createdFolder = mapper.valueToTree(newFolder);
        String absoluteUrl = routes.FolderController.findFolder(newFolder.getId()).absoluteURL(request());
        response().setHeader(HttpConstants.HTTP_HEADER_LOCATION, absoluteUrl);
        return created(createdFolder);
      } else {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("path", path);
        errorParams.put("name", name);
        errorParams.put("newFolderPath", candidatePath);
        return badRequest(generateErrorDescription("folderNotCreated",
            "The folder was not created:" + candidatePath, errorParams));
      }
    } catch (IllegalArgumentException e) {
      play.Logger.error("Illegal argument while creating the folder", e);
      return badRequestWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while creating the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result findFolder(String folderId) {
    try {
      IAuthRequest frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while reading the folder", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      String folderUUID = neo4JProxy.getFolderUUID(folderId);

      CedarFolder folder = neo4JProxy.findFolderById(folderUUID);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        errorParams.put("uuid", folderUUID);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        neo4JProxy.convertNeo4JValues(folder);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode folderNode = mapper.valueToTree(folder);
        return ok(folderNode);
      }
    } catch (Exception e) {
      play.Logger.error("Error while getting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result updateFolder(String folderId) {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while updating the folder", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode folderUpdateRequest = request().body().asJson();
      if (folderUpdateRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      String name = null;
      JsonNode nameNode = folderUpdateRequest.get("name");
      if (nameNode != null) {
        name = nameNode.asText();
        if (name != null) {
          name = name.trim();
        }
      }

      // test update folder name syntax
      if (name != null) {
        String normalizedName = neo4JProxy.getPathUtil().sanitizeName(name);
        if (!normalizedName.equals(name)) {
          throw new IllegalArgumentException("The folder name contains invalid characters!");
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
        throw new IllegalArgumentException("You must supply the new description or the new name of the folder!");
      }

      String folderUUID = neo4JProxy.getFolderUUID(folderId);
      CedarFolder folder = neo4JProxy.findFolderById(folderUUID);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        errorParams.put("uuid", folderUUID);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        Map<String, String> updateFields = new HashMap<>();
        if (description != null) {
          updateFields.put("description", description);
        }
        if (name != null) {
          updateFields.put("name", name);
        }
        CedarUser cu = Authorization.getAccountInfo(frontendRequest);
        CedarFolder updatedFolder = neo4JProxy.updateFolderById(folderUUID, updateFields, cu.getUserId());
        if (updatedFolder == null) {
          return notFound();
        } else {
          neo4JProxy.convertNeo4JValues(updatedFolder);
          ObjectMapper mapper = new ObjectMapper();
          JsonNode updatedFolderNode = mapper.valueToTree(updatedFolder);
          return ok(updatedFolderNode);
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result deleteFolder(String folderId) {
    try {
      IAuthRequest frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while deleting the folder", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      String folderUUID = neo4JProxy.getFolderUUID(folderId);
      CedarFolder folder = neo4JProxy.findFolderById(folderUUID);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        errorParams.put("uuid", folderUUID);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        // TODO: check folder contents, if not, delete only if "?force=true" param is present
        boolean deleted = neo4JProxy.deleteFolderById(folderUUID);
        if (deleted) {
          return noContent();
        } else {
          ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
          errorParams.put("id", folderId);
          errorParams.put("uuid", folderUUID);
          return internalServerErrorWithError(generateErrorDescription("folderNotDeleted",
              "The folder can not be delete by id:" + folderId, errorParams));
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

}
