package controllers;

import org.metadatacenter.constant.ConfigConstants;
import org.metadatacenter.server.play.AbstractCedarController;
import play.Configuration;
import play.Play;

public class AbstractFolderServerController extends AbstractCedarController {
  protected static Configuration config;
  protected static String USER_BASE_PATH;

  static {
    config = Play.application().configuration();
    USER_BASE_PATH = config.getString(ConfigConstants.USER_DATA_ID_PATH_BASE);
  }

  protected static Integer ensureLimit(Integer limit) {
    return limit == null ? config.getInt(ConfigConstants.PAGINATION_DEFAULT_PAGE_SIZE) : limit;
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
    int maxPageSize = config.getInt(ConfigConstants.PAGINATION_MAX_PAGE_SIZE);
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