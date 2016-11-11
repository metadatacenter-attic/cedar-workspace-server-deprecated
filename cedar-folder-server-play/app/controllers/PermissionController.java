package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.util.json.JsonMapper;
import play.mvc.Result;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

public class PermissionController extends AbstractFolderServerController {

  public static Result accessibleNodeIds() throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);

    try {
      PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);

      Map<String, String> ids = permissionSession.findAccessibleNodeIds();

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