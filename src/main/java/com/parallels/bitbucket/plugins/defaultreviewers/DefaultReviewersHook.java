package com.parallels.bitbucket.plugins.defaultreviewers;

import com.atlassian.bitbucket.hook.repository.RepositoryMergeCheck;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.PullRequestMergeHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestChangesRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.pull.UnmodifiablePullRequestRoleException;
import com.atlassian.bitbucket.content.AbstractChangeCallback;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeContext;
import com.atlassian.bitbucket.content.ChangeSummary;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.io.SingleLineOutputHandler;

import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.user.UserService;
import com.atlassian.bitbucket.util.Operation;
import com.atlassian.bitbucket.user.SecurityService;

import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.util.logging.Logger;


@Component("defaultReviewersHook")
public class DefaultReviewersHook implements RepositoryMergeCheck {
  private static final String PLUGIN_KEY = "com.parallels.bitbucket.plugins.defaultreviewers";

  private final PullRequestService pullRequestService;
  private final GitCommandBuilderFactory builderFactory;
  private final SecurityService securityService;
  private final UserService userService;
  private final PluginSettingsFactory pluginSettingsFactory;
  private static final Logger log = Logger.getLogger(DefaultReviewersHook.class.getName());

  @Autowired
  public DefaultReviewersHook (
    @ComponentImport final PullRequestService pullRequestService,
    @ComponentImport final GitCommandBuilderFactory builderFactory,
    @ComponentImport final SecurityService securityService,
    @ComponentImport final UserService userService,
    @ComponentImport final PluginSettingsFactory pluginSettingsFactory
  ) {
    this.pullRequestService = pullRequestService;
    this.builderFactory = builderFactory;
    this.securityService = securityService;
    this.userService = userService;
    this.pluginSettingsFactory = pluginSettingsFactory;
  }

  @Override
  public RepositoryHookResult preUpdate(PreRepositoryHookContext context,
        PullRequestMergeHookRequest request) {

    final PullRequest pullRequest = request.getPullRequest();
    final Repository repo = request.getToRef().getRepository();
    final String pluginSettingsId = String.format("%d.%d", repo.getId(), pullRequest.getId());

    final String newSha = request.getFromRef().getLatestCommit();

    if (newSha.equals(pluginSettingsFactory.createSettingsForKey(PLUGIN_KEY).get(pluginSettingsId))) {
        return RepositoryHookResult.accepted();
    }

    Path gitIndex = null;
    try {
        gitIndex = Files.createTempFile("git_idx_", "");
        final String gitIndexPath = gitIndex.toString();

        builderFactory.builder(repo)
          .command("read-tree")
          .argument(newSha)
          .withEnvironment("GIT_INDEX_FILE", gitIndexPath)
          .build(new SingleLineOutputHandler())
          .call();

        pullRequestService.streamChanges(
          new PullRequestChangesRequest.Builder(pullRequest).build(),
          new AbstractChangeCallback() {
            public boolean onChange(Change change) throws IOException {
              String changedFile = change.getPath().toString();
              List<String> ownersList = builderFactory.builder(repo)
                                    .command("check-attr")
                                    .argument("--cached")
                                    .argument("owners")
                                    .argument("--")
                                    .argument(changedFile)
                                    .withEnvironment("GIT_INDEX_FILE", gitIndexPath)
                                    .build(new GitCheckAttrOutputHandler())
                                    .call();
              if (ownersList == null) {
                return true;
              }
              for (String owner : ownersList) {
                ApplicationUser ownerUser = getUserByNameOrEmail(owner);
                if (ownerUser == null) {
                  log.warning("User not found by name or email: " + owner);
                } else {
                  try {
                    pullRequestService.addReviewer(repo.getId(), pullRequest.getId(), ownerUser.getName());
                  } catch (UnmodifiablePullRequestRoleException ignore) {
                    // noop, user is the pull request author
                  } catch (Exception e) {
                    log.severe("Failed to add reviewer: " + e.toString());
                  }
                }
              }
              return true;
            }
            public void onEnd(ChangeSummary summary) throws IOException {
              // noop
            }
            public void onStart(ChangeContext context) throws IOException {
              // noop
            }
          }
        );
        pluginSettingsFactory.createSettingsForKey(PLUGIN_KEY).put(pluginSettingsId, newSha);
    } catch (IOException e) {
      log.severe(e.toString());
    } finally {
      try {
        Files.delete(gitIndex);
      } catch (Exception e) { // IOException, NullPointerException
        log.warning(e.toString());
      }
      return RepositoryHookResult.accepted();
    }
  }

  public ApplicationUser getUserByNameOrEmail(final String userNameOrEmail) {
    ApplicationUser user = null;

    try {
      user = securityService.withPermission(Permission.REPO_ADMIN, "Find user").call(
        new Operation<ApplicationUser, Exception>() {
          @Override
          public ApplicationUser perform() throws Exception {
            return userService.findUserByNameOrEmail(userNameOrEmail);
          }
        }
      );
    } catch (Exception e) {
      log.severe(e.toString());
      return null;
    }

    return user;
  }

}
