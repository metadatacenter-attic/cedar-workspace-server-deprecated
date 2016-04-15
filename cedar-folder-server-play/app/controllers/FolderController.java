package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.exception.CedarCreationError;
import org.metadatacenter.model.CedarFolder;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Result;
import utils.DataServices;

import javax.management.InstanceNotFoundException;

public class FolderController extends AbstractFolderServerController {
  private static Logger log = LoggerFactory.getLogger(FolderController.class);

  public static Result createFolder() {
    try {
      IAuthRequest frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);

      JsonNode folderCreationRequest = request().body().asJson();
      if (folderCreationRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      // validate the path presence
      String path = null;
      JsonNode pathNode = folderCreationRequest.get("path");
      if (pathNode != null) {
        path = pathNode.asText();
        if (path != null) {
          path = path.trim();
        }
      }
      if (path == null || path.isEmpty()) {
        throw new IllegalArgumentException("You must supply the path of the new folder!");
      }

      // test path syntax
      String normalizedPath = neo4JProxy.normalizePath(path);
      if (!normalizedPath.equals(path)) {
        throw new IllegalArgumentException("You must supply the path of the new folder in normalized form!");
      }

      // test new folder name syntax
      String newFolderName = neo4JProxy.extractName(path);
      String normalizedName = neo4JProxy.sanitizeName(newFolderName);
      if (!normalizedName.equals(newFolderName)) {
        throw new IllegalArgumentException("The new folder name contains invalid characters!");
      }

      // validate the description presence
      String description = null;
      JsonNode descriptionNode = folderCreationRequest.get("description");
      if (descriptionNode != null) {
        description = descriptionNode.asText();
        if (description != null) {
          description = description.trim();
        }
      }
      if (description == null || description.isEmpty()) {
        throw new IllegalArgumentException("You must supply the description of the new folder !");
      }

      String parentFolderPath = neo4JProxy.getParentPath(path);

      CedarFolder newFolder = null;
      CedarFolder parentFolder = neo4JProxy.findFolderByPath(parentFolderPath);
      if (parentFolder == null) {
        return notFound();
      } else {
        CedarFolder newFolderCandidate = neo4JProxy.findFolderByPath(path);
        if (newFolderCandidate != null) {
          throw new IllegalArgumentException("There is already a folder with the path:" + path);
        }

        CedarUser cu = Authorization.getAccountInfo(frontendRequest);
        newFolder = neo4JProxy.createFolderAsChildOfId(parentFolder.getId(), newFolderName, description, cu.getUserId
            ());
        newFolder.setPath(path);
      }

      if (newFolder != null) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode createdFolder = mapper.valueToTree(newFolder);
        String absoluteUrl = routes.FolderController.findFolder(newFolder.getId()).absoluteURL(request());
        response().setHeader(HttpConstants.HTTP_HEADER_LOCATION, absoluteUrl);
        return created(createdFolder);
      } else {
        throw new CedarCreationError("The folder " + path + "was not created");
      }
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while creating the folder", e);
      return forbiddenWithError(e);
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

      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      CedarFolder folder = neo4JProxy.findFolderById(folderId);
      if (folder == null) {
        return notFound();
      } else {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode folderNode = mapper.valueToTree(folder);
        return ok(folderNode);
      }
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while getting the folder", e);
      return forbiddenWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while getting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result deleteFolder(String folderId) {
    try {
      IAuthRequest frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);

      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      CedarFolder folder = neo4JProxy.findFolderById(folderId);
      if (folder == null) {
        play.Logger.error("Folder not found while deleting:(" + folderId + ")");
        return notFound();
      } else {
        boolean deleted = neo4JProxy.deleteFolderById(folderId);
        if (deleted) {
          return noContent();
        } else {
          play.Logger.error("Unable to delete folder with id:" + folderId);
          return internalServerErrorWithError(null);
        }
      }
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while deleting the folder", e);
      return forbiddenWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while deleting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

}
