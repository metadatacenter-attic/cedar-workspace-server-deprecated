package controllers;

import org.metadatacenter.server.play.AbstractCedarController;
import play.mvc.Result;

public class MainFolderServerController extends AbstractFolderServerController {

  public static Result index() {
    return ok("CEDAR Folder Server.");
  }

  /* For CORS */
  public static Result preflight(String all) {
    return AbstractCedarController.preflight(all);
  }

}
