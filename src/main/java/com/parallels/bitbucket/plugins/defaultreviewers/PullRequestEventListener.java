package com.parallels.bitbucket.plugins.defaultreviewers;

import com.atlassian.bitbucket.event.pull.PullRequestDeclinedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestDeletedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestMergedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestEvent;

import com.atlassian.event.api.EventListener;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import java.util.logging.Logger;

import javax.inject.Named;


@Named("pullRequestEventListener")
public class PullRequestEventListener {
    private static final String PLUGIN_KEY = "com.parallels.bitbucket.plugins.defaultreviewers";
    private static final Logger log = Logger.getLogger(PullRequestEventListener.class.getName());

    private final PluginSettingsFactory pluginSettingsFactory;

    public PullRequestEventListener(
      final PluginSettingsFactory pluginSettingsFactory
    ) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @EventListener
    public void onPullRequestDeclined(PullRequestDeclinedEvent event) {
        cleanupPluginSettings(getPluginSettingsId(event));
    }

    @EventListener
    public void onPullRequestDeleted(PullRequestDeletedEvent event) {
        cleanupPluginSettings(getPluginSettingsId(event));
    }

    @EventListener
    public void onPullRequestMerged(PullRequestMergedEvent event) {
        cleanupPluginSettings(getPluginSettingsId(event));
    }

    private String getPluginSettingsId(PullRequestEvent event) {
        return String.format("%d.%d", event.getPullRequest().getFromRef().getRepository().getId(), event.getPullRequest().getId());
    }

    private void cleanupPluginSettings(String pluginSettingsId) {
        try {
            pluginSettingsFactory.createSettingsForKey(PLUGIN_KEY).remove(pluginSettingsId);
        } catch (NullPointerException e) {
            log.info(e.toString());
        }
    }
}
