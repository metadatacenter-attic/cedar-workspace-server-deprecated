package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.folderserver.FolderServerUser;
import org.metadatacenter.model.response.FolderServerUserListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.util.json.JsonMapper;
import play.mvc.Result;

import java.util.List;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

public class UserController extends AbstractFolderServerController {


  public static Result findUsers() throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request());
    c.must(c.user()).be(LoggedIn);

    try {
      UserServiceSession userSession = CedarDataServices.getUserServiceSession(c);

      List<FolderServerUser> users = userSession.findUsers();

      FolderServerUserListResponse r = new FolderServerUserListResponse();

      r.setUsers(users);

      JsonNode resp = JsonMapper.MAPPER.valueToTree(r);
      return ok(resp);

    } catch (Exception e) {
      play.Logger.error("Error while listing users", e);
      return internalServerErrorWithError(e);
    }
  }

}