package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.AuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.json.JsonMapper;
import play.mvc.Result;
import utils.DataServices;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class PermissionController extends AbstractFolderServerController {

  public static Result accessibleNodeIds() {
    AuthRequest frontendRequest;
    CedarUser currentUser;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while reading the users", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      Map<String, String> ids = neoSession.findAccessibleNodeIds();

      Map<String, Object> r = new IdentityHashMap<>();
      r.put("accessibleNodes", ids);

      JsonNode resp = JsonMapper.MAPPER.valueToTree(r);
      return ok(resp);

    } catch (Exception e) {
      play.Logger.error("Error while listing accessible nodes", e);
      return internalServerErrorWithError(e);
    }
  }
}