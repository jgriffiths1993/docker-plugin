package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.utils.DockerImageName;
import com.nirima.jenkins.plugins.docker.utils.DockerImageName.TagName;
import com.nirima.jenkins.plugins.docker.utils.DockerImageNameException;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;


public class DockerJobProperty extends JobProperty<AbstractProject<?, ?>> {
    
    // Empty constructor - using DataBoundSetter instead
    @DataBoundConstructor public DockerJobProperty() {}
    
    // Use a container for the job (expands other options)
    @DataBoundSetter public boolean hasContainer;
    
    // Commit container when build completes
    @DataBoundSetter public boolean commitContainer;
    
    // Remove committed image
    @DataBoundSetter public boolean cleanImages;
    
    //Keep containers running after successful builds
    @DataBoundSetter public boolean remainsRunning;

    //Author of the image
    @DataBoundSetter public String imageAuthor;

    // Tag committed image as 'latest'
    @DataBoundSetter public boolean tagLatest;
    
    // Tag the committed image with the build number
    @DataBoundSetter public boolean tagBuildNumber;
    
    // Repository name to use for the image, including domain/namespace
    @DataBoundSetter public String repositoryName;

    // List of tags, delimited by commas or semi-colons
    @DataBoundSetter public String imageTags;
    
     // Kept for backwards compatibility with existing data
    @DataBoundSetter public String additionalTag;
    
    // Push image to registry after commit
    @DataBoundSetter public Boolean pushImage;

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public String getDisplayName() {
            return "Docker Job Properties";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        public FormValidation doCheckRepositoryName(@QueryParameter String repositoryName) {
            // Empty string is valid as we'll use the 
            if (Strings.isNullOrEmpty(repositoryName)) {
                return FormValidation.ok();
            }
            DockerImageName imageName = new DockerImageName(repositoryName);
            // validate will throw an exception if it's invalid. Give the message to the user.
            try {
                imageName.validate();
            } catch (DockerImageNameException ex) {
                return FormValidation.error(ex.getMessage());
            }
            // Let the user know if they're using a registry. Fairly helpful.
            if (imageName.getRegistry() != null) {
                return FormValidation.ok("Using registry: " + imageName.getRegistry().toString());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckImageTags(@QueryParameter String imageTags) {
            // It's allowed to be empty
            if (Strings.isNullOrEmpty(imageTags)) {
                return FormValidation.ok();
            }
            // Split them by commas
            String[] tags = imageTags.split("[,]+");
            // Iterate and check the tags 
            for(String tag : tags) {
                try {
                    new TagName(tag).validate();
                } catch (DockerImageNameException ex) {
                    return FormValidation.error(ex.getMessage());
                }
            }
            return FormValidation.ok();
        }
        
        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (req.hasParameter("hasContainer")) {
                return req.bindJSON(DockerJobProperty.class, formData);
            }
            return null;
        }
    }
}
