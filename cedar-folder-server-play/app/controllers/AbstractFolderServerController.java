package controllers;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.play.AbstractCedarController;

public class AbstractFolderServerController extends AbstractCedarController {

  protected static final CedarConfig cedarConfig;

  static {
    cedarConfig = CedarConfig.getInstance();
  }

  protected static Integer ensureLimit(Integer limit) {
    return limit == null ? cedarConfig.getFolderRESTAPI().getPagination().getDefaultPageSize() : limit;
  }

  protected static void checkPagingParameters(Integer limit, Integer offset) {
    // check offset
    if (offset < 0) {
      throw new IllegalArgumentException("Parameter 'offset' must be positive!");
    }
    // check limit
    if (limit <= 0) {
      throw new IllegalArgumentException("Parameter 'limit' must be greater than zero!");
    }
    int maxPageSize = cedarConfig.getFolderRESTAPI().getPagination().getDefaultPageSize();
    if (limit > maxPageSize) {
      throw new IllegalArgumentException("Parameter 'limit' must be at most " + maxPageSize + "!");
    }
  }

  protected static void checkPagingParametersAgainstTotal(Integer offset, long total) {
    if (offset != 0 && offset > total - 1) {
      throw new IllegalArgumentException("Parameter 'offset' must be smaller than the total count of objects, which " +
          "is " + total + "!");
    }
  }

}