package com.parallels.bitbucket.plugins.defaultreviewers;

import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.utils.process.Watchdog;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import javax.annotation.Nullable;

import java.util.logging.Logger;


public class GitCheckAttrOutputHandler implements CommandOutputHandler<List<String>> {
  private ArrayList<String> attrValueList;
  private static final Logger log = Logger.getLogger(GitCheckAttrOutputHandler.class.getName());

  @Nullable
  @Override
  public List<String> getOutput() {
    return attrValueList;
  }

  @Override
  public void complete() {
  }

  @Override
  public void setWatchdog(Watchdog watchdog) {
  }

  @Override
  public void process(InputStream output) {
    attrValueList = new ArrayList<String>();

    String nextLine;
    try (BufferedReader lineReader = new BufferedReader(new InputStreamReader(output))) {
      while ((nextLine = lineReader.readLine()) != null) {
        try {
          String attrValueStr = nextLine.split(":")[2].trim();
          if (!attrValueStr.equals("unspecified")) {
            for (String attrValue : attrValueStr.split(",")) {
              attrValueList.add(attrValue);
            }
          }
        } catch (IndexOutOfBoundsException e) {
          log.severe("Could not parse git attr value from '" + nextLine + "'");
        }
      }
    } catch (IOException e) {
      log.severe(e.toString());
    }
  }
}
