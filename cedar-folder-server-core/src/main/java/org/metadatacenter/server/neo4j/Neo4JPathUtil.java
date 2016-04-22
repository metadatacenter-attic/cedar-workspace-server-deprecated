package org.metadatacenter.server.neo4j;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Neo4JPathUtil implements IPathUtil {

  private final static String SEPARATOR = "/";
  private final static String FOLDER_NAME_SANITIZER_REGEX = "[^a-zA-Z0-9\\.\\-_' ]";
  private final static Pattern FOLDER_NAME_SANITIZER_PATTERN = Pattern.compile(FOLDER_NAME_SANITIZER_REGEX);

  private final String rootPath;

  Neo4JPathUtil(Neo4jConfig config) {
    rootPath = config.getRootFolderPath();
  }

  private boolean isRoot(String path) {
    String normalizedPath = normalizePath(path);
    return rootPath.equals(normalizedPath);
  }

  @Override
  public String getSeparator() {
    return SEPARATOR;
  }

  @Override
  public String getRootPath() {
    return rootPath;
  }

  @Override
  public String getChildPath(String parent, String name) {
    return new StringBuilder().append(parent).append(SEPARATOR).append(name).toString();
  }

  @Override
  public String normalizePath(String path) {
    if (path != null) {
      if (!rootPath.equals(path)) {
        while (path.endsWith(SEPARATOR)) {
          path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith(rootPath)) {
          path = rootPath + path;
        }
        String ds = SEPARATOR + SEPARATOR;
        while (path.indexOf(ds) != -1) {
          path = path.replace(ds, SEPARATOR);
        }
      }
    }
    return path;
  }

  @Override
  public String sanitizeName(String name) {
    Matcher matcher = FOLDER_NAME_SANITIZER_PATTERN.matcher(name);
    return matcher.replaceAll("_");
  }

  @Override
  public String extractName(String path) {
    if (path != null) {
      if (isRoot(path)) {
        return path;
      } else {
        int i = path.lastIndexOf(SEPARATOR);
        if (i > -1) {
          return path.substring(i + SEPARATOR.length());
        }
      }
    }
    return null;
  }

/*  public String getParentPath(String path) {
    if (path != null) {
      String[] split = StringUtils.split(path, SEPARATOR);
      if (split != null) {
        if (split.length > 0) {
          String parent = StringUtils.join(Arrays.copyOf(split, split.length - 1), SEPARATOR);
          return normalizePath(parent);
        }
      } else {
        return rootPath;
      }
    }
    return null;
  }*/

  @Override
  public int getPathDepth(String path) {
    String normalizedPath = normalizePath(path);
    return isRoot(normalizedPath) ? 1 : StringUtils.countMatches(normalizedPath, SEPARATOR) + 1;
  }

}