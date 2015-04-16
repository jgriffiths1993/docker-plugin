package com.nirima.jenkins.plugins.docker.action;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.ExportedBean;

/**
 *
 * @author jgriffiths1993 06/05/2015
 */
@ExportedBean
public class BuiltOnDockerAction implements Action, Serializable, Cloneable, Describable<BuiltOnDockerAction> {
    
    // Construct with these as they should always be relevant
    public final String containerId;
    public final String dockerHost;
    
    // Set these during slave shutdown if they're relevant
    public String imageId;
    public String remoteFsMapping;
    public String repositoryName;
    public ArrayList<String> imageTags;
    
    public BuiltOnDockerAction(String containerId, String dockerHost) {
        this.containerId = containerId;
        this.dockerHost = dockerHost;
        this.imageId = null;
        this.remoteFsMapping = null;
        this.imageTags = null;
    }
    
    @Override
    public String getDisplayName() {
        return "Built on Docker";
    }
    
    @Override
    public String getIconFileName() {
        return "/plugin/docker-plugin/images/24x24/docker.png";
    }
    
    @Override
    public String getUrlName() {
        return "docker";
    }
    
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<BuiltOnDockerAction> {
        public String getDisplayName() {
            return "Docker";
        }
    }
}